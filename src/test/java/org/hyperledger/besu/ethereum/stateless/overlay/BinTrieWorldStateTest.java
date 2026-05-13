/*
 * Copyright Hyperledger Besu Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.ethereum.stateless.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.stateless.bintrie.BytesBitSequence;
import org.hyperledger.besu.ethereum.stateless.bintrie.SimpleBinTrie;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyFactory;
import org.hyperledger.besu.ethereum.stateless.bintrie.adapter.TrieKeyUtils;
import org.hyperledger.besu.ethereum.stateless.bintrie.hasher.StemHasher;
import org.hyperledger.besu.ethereum.stateless.bintrie.util.SuffixTreeEncoder;
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.Keccak;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptAccount;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BinTrieWorldStateTest {

  private static final Bytes ADDRESS = Bytes.fromHexString("0x" + "11".repeat(20));
  // 5 bytes of code: PUSH1 0x01, PUSH1 0x01, ADD
  private static final Bytes CODE = Bytes.fromHexString("0x6001600101");

  private TrieKeyFactory keyFactory;
  private InMemoryCodeStore codeStore;
  private BinTrieWorldState worldState;

  @BeforeEach
  public void setUp() {
    keyFactory = new TrieKeyFactory(new StemHasher());
    codeStore = new InMemoryCodeStore();
    worldState =
        new BinTrieWorldState(
            new SimpleBinTrie<BytesBitSequence, Bytes32>(), keyFactory, codeStore);
  }

  @Test
  public void testPutAccountMatchesDirectTriePuts() {
    worldState.putAccount(ADDRESS, 7, UInt256.valueOf(1000));

    SimpleBinTrie<BytesBitSequence, Bytes32> reference = new SimpleBinTrie<>();
    Bytes32 basicData = Bytes32.ZERO;
    basicData = SuffixTreeEncoder.setVersionInValue(basicData, Bytes.of(0));
    basicData = SuffixTreeEncoder.setNonceInValue(basicData, Bytes.ofUnsignedLong(7));
    basicData = SuffixTreeEncoder.setBalanceInValue(basicData, UInt256.valueOf(1000).slice(16, 16));
    reference.put(keyFactory.basicDataKey(ADDRESS), basicData);
    reference.put(keyFactory.codeHashKey(ADDRESS), MptAccount.EMPTY_CODE_HASH);

    assertThat(worldState.getRootHash()).isEqualTo(reference.getRootHash());
  }

  @Test
  public void testPutCodeMatchesDirectTriePuts() {
    worldState.putAccount(ADDRESS, 0, UInt256.ONE);
    worldState.putCode(ADDRESS, CODE);

    SimpleBinTrie<BytesBitSequence, Bytes32> reference = new SimpleBinTrie<>();
    Bytes32 basicData = Bytes32.ZERO;
    basicData = SuffixTreeEncoder.setVersionInValue(basicData, Bytes.of(0));
    basicData = SuffixTreeEncoder.setNonceInValue(basicData, Bytes.ofUnsignedLong(0));
    basicData = SuffixTreeEncoder.setBalanceInValue(basicData, UInt256.ONE.slice(16, 16));
    basicData = SuffixTreeEncoder.setCodeSizeInValue(basicData, BinTrieWorldState.codeSizeBytes(5));
    reference.put(keyFactory.basicDataKey(ADDRESS), basicData);
    reference.put(keyFactory.codeHashKey(ADDRESS), Keccak.keccak256(CODE));
    List<UInt256> chunks = TrieKeyUtils.chunkifyCode(CODE);
    for (int i = 0; i < chunks.size(); i++) {
      reference.put(keyFactory.codeChunkKey(ADDRESS, UInt256.valueOf(i)), chunks.get(i).toBytes());
    }

    assertThat(worldState.getRootHash()).isEqualTo(reference.getRootHash());
  }

  @Test
  public void testAccountAndCodeFieldsAreIndependent() {
    // putAccount then putCode, vs putCode fields first in a fresh state: same leaf content
    worldState.putAccount(ADDRESS, 3, UInt256.valueOf(5));
    worldState.putCode(ADDRESS, CODE);
    Bytes32 rootCodeLast = worldState.getRootHash();

    BinTrieWorldState other =
        new BinTrieWorldState(
            new SimpleBinTrie<BytesBitSequence, Bytes32>(), keyFactory, new InMemoryCodeStore());
    other.putAccount(ADDRESS, 0, UInt256.ZERO);
    other.putCode(ADDRESS, CODE);
    other.putAccount(ADDRESS, 3, UInt256.valueOf(5));
    assertThat(other.getRootHash()).isEqualTo(rootCodeLast);
  }

  @Test
  public void testReadBack() {
    worldState.putAccount(ADDRESS, 9, UInt256.valueOf(77));
    worldState.putCode(ADDRESS, CODE);
    worldState.putStorage(ADDRESS, UInt256.valueOf(300), UInt256.valueOf(12345));

    AccountState account = worldState.getAccount(ADDRESS).orElseThrow();
    assertThat(account.nonce()).isEqualTo(9);
    assertThat(account.balance()).isEqualTo(UInt256.valueOf(77));
    assertThat(account.codeHash()).isEqualTo(Keccak.keccak256(CODE));
    assertThat(account.codeSize()).isEqualTo(5);
    assertThat(worldState.getCode(ADDRESS)).contains(CODE);
    assertThat(worldState.getStorage(ADDRESS, UInt256.valueOf(300)))
        .contains(UInt256.valueOf(12345));
    assertThat(worldState.getStorage(ADDRESS, UInt256.valueOf(301))).isEmpty();
  }

  @Test
  public void testZeroStorageValueRemoves() {
    worldState.putAccount(ADDRESS, 0, UInt256.ONE);
    Bytes32 rootBefore = worldState.getRootHash();
    worldState.putStorage(ADDRESS, UInt256.valueOf(4), UInt256.valueOf(42));
    worldState.putStorage(ADDRESS, UInt256.valueOf(4), UInt256.ZERO);
    assertThat(worldState.getRootHash()).isEqualTo(rootBefore);
    assertThat(worldState.getStorage(ADDRESS, UInt256.valueOf(4))).isEmpty();
  }

  @Test
  public void testRemoveAccount() {
    SimpleBinTrie<BytesBitSequence, Bytes32> empty = new SimpleBinTrie<>();
    worldState.putAccount(ADDRESS, 0, UInt256.ONE);
    worldState.putCode(ADDRESS, CODE);
    worldState.putStorage(ADDRESS, UInt256.valueOf(2), UInt256.valueOf(3));
    worldState.removeAccount(ADDRESS, List.of(UInt256.valueOf(2)), CODE);

    assertThat(worldState.getAccount(ADDRESS)).isEmpty();
    assertThat(worldState.getRootHash()).isEqualTo(empty.getRootHash());
  }

  @Test
  public void testStoredRoundTrip() {
    NodeStoreMock nodeStore = new NodeStoreMock();
    BinTrieWorldState stored = BinTrieWorldState.stored(nodeStore, codeStore);
    stored.putAccount(ADDRESS, 1, UInt256.valueOf(11));
    stored.putStorage(ADDRESS, UInt256.valueOf(70), UInt256.valueOf(9));
    Bytes32 root = stored.getRootHash();
    stored.commit(nodeStore);

    BinTrieWorldState reloaded = BinTrieWorldState.stored(nodeStore, codeStore);
    assertThat(reloaded.getRootHash()).isEqualTo(root);
    assertThat(reloaded.getAccount(ADDRESS).orElseThrow().balance()).isEqualTo(UInt256.valueOf(11));
    assertThat(reloaded.getStorage(ADDRESS, UInt256.valueOf(70))).contains(UInt256.valueOf(9));
  }
}
