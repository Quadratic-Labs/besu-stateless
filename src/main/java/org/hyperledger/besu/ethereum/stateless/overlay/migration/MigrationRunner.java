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
package org.hyperledger.besu.ethereum.stateless.overlay.migration;

import org.hyperledger.besu.ethereum.stateless.bintrie.BytesBitSequence;
import org.hyperledger.besu.ethereum.stateless.overlay.WorldStateReader;
import org.hyperledger.besu.ethereum.stateless.overlay.bintrie.BinTrieWorldState;

import java.util.List;

/**
 * Drives the chunked background migration: consumes stem units from the {@link MigrationSource},
 * resolves them against the live Merkle Patricia world state via {@link StemMigrator}, and inserts
 * the resulting leaves into the binary trie.
 *
 * <p>Chunks are idempotent: re-running a chunk against the same world state re-inserts identical
 * values, so replay after a crash converges to the same trie.
 */
public class MigrationRunner {

  private final MigrationSource source;
  private final StemMigrator migrator;
  private final WorldStateReader mptWorldState;
  private final BinTrieWorldState binTrieWorldState;
  private long frontier;
  private BytesBitSequence lastStem;

  /**
   * Creates a runner positioned at the given frontier.
   *
   * @param source The stem enumeration; it is sought to {@code frontier}.
   * @param migrator The stem migrator used to resolve values.
   * @param mptWorldState The live Merkle Patricia world state (canonical read source).
   * @param binTrieWorldState The binary trie world state to insert migrated leaves into.
   * @param frontier The number of stem units already migrated (zero for a fresh transition).
   */
  public MigrationRunner(
      final MigrationSource source,
      final StemMigrator migrator,
      final WorldStateReader mptWorldState,
      final BinTrieWorldState binTrieWorldState,
      final long frontier) {
    this.source = source;
    this.migrator = migrator;
    this.mptWorldState = mptWorldState;
    this.binTrieWorldState = binTrieWorldState;
    this.frontier = frontier;
    source.seek(frontier);
  }

  /**
   * Migrates up to the given number of stem units. Units whose members all resolve to nothing still
   * consume quota, so the frontier advances deterministically.
   *
   * @param maxStems The chunk size in stem units.
   * @return The number of units consumed.
   */
  public int runChunk(final int maxStems) {
    int migrated = 0;
    while (migrated < maxStems && source.hasNext()) {
      final StemMigrationUnit unit = source.next();
      if (lastStem != null && unit.stem().compareTo(lastStem) <= 0) {
        throw new IllegalStateException(
            "MigrationSource violated stem ordering: "
                + unit.stem().toHexString()
                + " after "
                + lastStem.toHexString());
      }
      final List<MigratedLeaf> leaves = migrator.migrate(unit, mptWorldState);
      for (final MigratedLeaf leaf : leaves) {
        binTrieWorldState.putRaw(leaf.key(), leaf.value());
      }
      lastStem = unit.stem();
      frontier++;
      migrated++;
    }
    return migrated;
  }

  /**
   * Whether the enumeration is exhausted.
   *
   * @return True once every stem unit has been migrated.
   */
  public boolean isComplete() {
    return !source.hasNext();
  }

  /**
   * The number of stem units remaining.
   *
   * @return The remaining unit count.
   */
  public long remaining() {
    return source.remaining();
  }

  /**
   * The current progress.
   *
   * @return The progress (frontier and completion flag).
   */
  public MigrationProgress progress() {
    return new MigrationProgress(frontier, isComplete());
  }
}
