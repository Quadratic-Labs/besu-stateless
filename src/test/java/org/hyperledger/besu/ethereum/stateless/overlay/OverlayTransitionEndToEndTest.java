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
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.Keccak;
import org.hyperledger.besu.ethereum.stateless.overlay.mpt.MptAccount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * End-to-end property test: a full transition with a random interleaved workload must produce, at
 * cutover, exactly the binary trie a fresh build of the final semantic state produces — and the
 * whole per-block root sequence must be deterministic.
 */
public class OverlayTransitionEndToEndTest {

  private static final int ACCOUNTS = 50;
  private static final int SPARE_BLOCKS = 4;
  private static final int STEMS_PER_BLOCK = 7;

  private static Bytes addressOf(int i) {
    return Bytes.fromHexString(String.format("0x%040x", i + 1));
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 7, 42, 1337, 99999})
  public void testRandomWorkloadTransitionMatchesReference(long seed) {
    TransitionRun first = runOnce(seed);
    assertThat(first.finalBinRoot).isEqualTo(first.referenceRoot);

    // determinism: a second run with the same seed reproduces the entire root sequence
    TransitionRun second = runOnce(seed);
    assertThat(second.perBlockBinRoots).isEqualTo(first.perBlockBinRoots);
    assertThat(second.perBlockMptRoots).isEqualTo(first.perBlockMptRoots);

    // order independence: inserting the final leaf set in shuffled order gives the same root
    assertThat(shuffledReferenceRoot(first, seed)).isEqualTo(first.referenceRoot);
  }

  private record TransitionRun(
      Bytes32 finalBinRoot,
      Bytes32 referenceRoot,
      List<Bytes32> perBlockBinRoots,
      List<Bytes32> perBlockMptRoots,
      SemanticStateModel finalModel,
      TrieKeyFactory keyFactory) {}

  private TransitionRun runOnce(long seed) {
    OverlayTransitionFixture fixture = new OverlayTransitionFixture();

    // pre-fork state: EOAs and contracts with multi-chunk code and boundary-straddling storage
    for (int i = 0; i < ACCOUNTS; i++) {
      final int index = i;
      fixture.preFork(
          w -> {
            Bytes address = addressOf(index);
            w.putAccount(address, index, UInt256.valueOf(1000L + index));
            if (index % 2 == 0) {
              byte[] code = new byte[31 * (1 + index % 5) + index % 7];
              new Random(seed + index).nextBytes(code);
              w.putCode(address, Bytes.wrap(code));
              w.putStorage(address, UInt256.valueOf(index % 70), UInt256.valueOf(index + 1));
              w.putStorage(address, UInt256.valueOf(5000 + index), UInt256.valueOf(index + 2));
            }
          });
    }

    fixture.startTransitionAuto(100, STEMS_PER_BLOCK, SPARE_BLOCKS);
    Random random = new Random(seed * 31);
    List<Bytes32> binRoots = new ArrayList<>();
    List<Bytes32> mptRoots = new ArrayList<>();
    while (fixture.nextBlock <= fixture.config.targetBlock()) {
      List<Consumer<WorldStateWriter>> blockOps = new ArrayList<>();
      if (fixture.nextBlock < fixture.config.targetBlock()) {
        // Generate this block's ops against a planning copy of the model so each op's frozen
        // decisions (target account, its storage keys, its code) reflect the effect of the
        // preceding ops of the same block; the identical list then runs on overlay and model.
        SemanticStateModel planning = fixture.model.copy();
        int opCount = 5 + random.nextInt(11);
        for (int i = 0; i < opCount; i++) {
          Consumer<WorldStateWriter> op = nextOp(random, planning);
          op.accept(planning);
          blockOps.add(op);
        }
      }
      BlockCommitResult result = fixture.runBlock(w -> blockOps.forEach(op -> op.accept(w)));
      binRoots.add(result.binTrieRootHash());
      mptRoots.add(result.mptRootHash());
    }
    return new TransitionRun(
        fixture.overlay.getBinTrieRootHash(),
        fixture.referenceRoot(),
        binRoots,
        mptRoots,
        fixture.model,
        fixture.keyFactory);
  }

  /**
   * Generates one random operation as a reusable consumer. All decisions are frozen at generation
   * time from the planning model, so replaying the consumer is deterministic.
   */
  private Consumer<WorldStateWriter> nextOp(Random random, SemanticStateModel planning) {
    List<Bytes> existing = new ArrayList<>(planning.accounts.keySet());
    int op = random.nextInt(10);
    if (existing.isEmpty() || op <= 2) {
      Bytes address = addressOf(random.nextInt(ACCOUNTS * 2));
      long nonce = random.nextInt(100);
      UInt256 balance = UInt256.valueOf(random.nextInt(1_000_000));
      return w -> w.putAccount(address, nonce, balance);
    }
    Bytes address = existing.get(random.nextInt(existing.size()));
    switch (op) {
      case 3, 4 -> {
        long nonce = random.nextInt(100);
        UInt256 balance = UInt256.valueOf(random.nextInt(1_000_000));
        return w -> w.putAccount(address, nonce, balance);
      }
      case 5, 6 -> {
        UInt256 slot =
            switch (random.nextInt(4)) {
              case 0 -> UInt256.valueOf(random.nextInt(64));
              case 1 -> UInt256.valueOf(60 + random.nextInt(10));
              default -> UInt256.valueOf(random.nextInt(10_000));
            };
        UInt256 value =
            random.nextInt(4) == 0 ? UInt256.ZERO : UInt256.valueOf(1 + random.nextInt(1_000));
        return w -> w.putStorage(address, slot, value);
      }
      case 7, 8 -> {
        byte[] code = new byte[random.nextInt(120)];
        random.nextBytes(code);
        Bytes codeBytes = Bytes.wrap(code);
        return w -> w.putCode(address, codeBytes);
      }
      default -> {
        List<UInt256> slots = new ArrayList<>(planning.storageOf(address).keySet());
        Bytes code = planning.codeOf(address);
        return w -> w.removeAccount(address, slots, code);
      }
    }
  }

  /** Rebuilds the final leaf set and inserts it in shuffled order; the root must not change. */
  private Bytes32 shuffledReferenceRoot(TransitionRun run, long seed) {
    record Leaf(BytesBitSequence key, Bytes32 value) {}
    List<Leaf> leaves = new ArrayList<>();
    SemanticStateModel model = run.finalModel;
    TrieKeyFactory keyFactory = run.keyFactory;
    model.accounts.forEach(
        (address, account) -> {
          Bytes code = model.codeOf(address);
          Bytes32 codeHash = code.isEmpty() ? MptAccount.EMPTY_CODE_HASH : Keccak.keccak256(code);
          leaves.add(
              new Leaf(
                  keyFactory.basicDataKey(address),
                  BinTrieWorldState.encodeBasicData(
                      account.nonce(), account.balance(), code.size())));
          leaves.add(new Leaf(keyFactory.codeHashKey(address), codeHash));
          List<UInt256> chunks = TrieKeyUtils.chunkifyCode(code);
          for (int i = 0; i < chunks.size(); i++) {
            leaves.add(
                new Leaf(
                    keyFactory.codeChunkKey(address, UInt256.valueOf(i)), chunks.get(i).toBytes()));
          }
          model
              .storageOf(address)
              .forEach(
                  (slot, value) ->
                      leaves.add(new Leaf(keyFactory.storageKey(address, slot), value.toBytes())));
        });
    Collections.shuffle(leaves, new Random(seed + 12345));
    SimpleBinTrie<BytesBitSequence, Bytes32> trie = new SimpleBinTrie<>();
    leaves.forEach(leaf -> trie.put(leaf.key(), leaf.value()));
    return trie.getRootHash();
  }
}
