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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptAccount;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptWorldState;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MptWorldStateTest {

  private static final Bytes ADDRESS = Bytes.fromHexString("0x" + "11".repeat(20));
  private static final Bytes OTHER_ADDRESS = Bytes.fromHexString("0x" + "22".repeat(20));

  private NodeStoreMock nodeStore;
  private InMemoryCodeStore codeStore;
  private MptWorldState worldState;

  @BeforeEach
  public void setUp() {
    nodeStore = new NodeStoreMock();
    codeStore = new InMemoryCodeStore();
    worldState = new MptWorldState(nodeStore, codeStore);
  }

  @Test
  public void testEmptyAccountRlpMatchesKnownVector() {
    MptAccount account =
        new MptAccount(0, UInt256.ZERO, MptAccount.EMPTY_TRIE_HASH, MptAccount.EMPTY_CODE_HASH);
    assertThat(account.toRlp())
        .isEqualTo(
            Bytes.fromHexString(
                "0xf8448080"
                    + "a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"
                    + "a0c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"));
  }

  @Test
  public void testAccountRlpRoundTrip() {
    MptAccount account =
        new MptAccount(
            42,
            UInt256.valueOf(123456789L),
            Bytes32.fromHexString("0x" + "ab".repeat(32)),
            Bytes32.fromHexString("0x" + "cd".repeat(32)));
    assertThat(MptAccount.fromRlp(account.toRlp())).isEqualTo(account);
  }

  @Test
  public void testPutAndGetAccount() {
    worldState.putAccount(ADDRESS, 7, UInt256.valueOf(1000));
    AccountState account = worldState.getAccount(ADDRESS).orElseThrow();
    assertThat(account.nonce()).isEqualTo(7);
    assertThat(account.balance()).isEqualTo(UInt256.valueOf(1000));
    assertThat(account.codeHash()).isEqualTo(MptAccount.EMPTY_CODE_HASH);
    assertThat(account.codeSize()).isEqualTo(0);
    assertThat(worldState.getAccount(OTHER_ADDRESS)).isEmpty();
  }

  @Test
  public void testPutAccountPreservesCodeAndStorage() {
    worldState.putAccount(ADDRESS, 0, UInt256.ONE);
    worldState.putCode(ADDRESS, Bytes.fromHexString("0x6001600101"));
    worldState.putStorage(ADDRESS, UInt256.ONE, UInt256.valueOf(99));
    worldState.putAccount(ADDRESS, 1, UInt256.valueOf(2));

    AccountState account = worldState.getAccount(ADDRESS).orElseThrow();
    assertThat(account.nonce()).isEqualTo(1);
    assertThat(account.codeSize()).isEqualTo(5);
    assertThat(worldState.getCode(ADDRESS)).contains(Bytes.fromHexString("0x6001600101"));
    assertThat(worldState.getStorage(ADDRESS, UInt256.ONE)).contains(UInt256.valueOf(99));
  }

  @Test
  public void testStorageFoldChangesRootAndZeroingRestoresIt() {
    worldState.putAccount(ADDRESS, 0, UInt256.ONE);
    Bytes32 rootWithoutStorage = worldState.getRootHash();

    worldState.putStorage(ADDRESS, UInt256.ZERO, UInt256.valueOf(7));
    Bytes32 rootWithStorage = worldState.getRootHash();
    assertThat(rootWithStorage).isNotEqualTo(rootWithoutStorage);

    worldState.putStorage(ADDRESS, UInt256.ZERO, UInt256.ZERO);
    assertThat(worldState.getRootHash()).isEqualTo(rootWithoutStorage);
    assertThat(worldState.getStorage(ADDRESS, UInt256.ZERO)).isEmpty();
  }

  @Test
  public void testPutStorageForMissingAccountThrows() {
    assertThatThrownBy(() -> worldState.putStorage(ADDRESS, UInt256.ZERO, UInt256.ONE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testPutCodeForMissingAccountThrows() {
    assertThatThrownBy(() -> worldState.putCode(ADDRESS, Bytes.of(1)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void testRemoveAccount() {
    worldState.putAccount(ADDRESS, 0, UInt256.ONE);
    Bytes32 rootWithOneAccount = worldState.getRootHash();

    worldState.putAccount(OTHER_ADDRESS, 0, UInt256.ONE);
    worldState.putStorage(OTHER_ADDRESS, UInt256.ZERO, UInt256.ONE);
    worldState.removeAccount(OTHER_ADDRESS, java.util.List.of(UInt256.ZERO), Bytes.EMPTY);

    assertThat(worldState.getAccount(OTHER_ADDRESS)).isEmpty();
    assertThat(worldState.getRootHash()).isEqualTo(rootWithOneAccount);
  }

  @Test
  public void testCommitAndReload() {
    worldState.putAccount(ADDRESS, 3, UInt256.valueOf(55));
    worldState.putCode(ADDRESS, Bytes.fromHexString("0x60ff"));
    worldState.putStorage(ADDRESS, UInt256.valueOf(2), UInt256.valueOf(1234));
    Bytes32 root = worldState.getRootHash();
    worldState.commit(nodeStore);

    MptWorldState reloaded = new MptWorldState(nodeStore, root, codeStore);
    assertThat(reloaded.getRootHash()).isEqualTo(root);
    AccountState account = reloaded.getAccount(ADDRESS).orElseThrow();
    assertThat(account.nonce()).isEqualTo(3);
    assertThat(account.balance()).isEqualTo(UInt256.valueOf(55));
    assertThat(reloaded.getCode(ADDRESS)).contains(Bytes.fromHexString("0x60ff"));
    assertThat(reloaded.getStorage(ADDRESS, UInt256.valueOf(2))).contains(UInt256.valueOf(1234));
  }
}
