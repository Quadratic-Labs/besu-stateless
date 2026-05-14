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

import java.util.Optional;

/**
 * Client-supplied persistence for {@link MigrationProgress}.
 *
 * <p>{@code save} is called once per block, after both tries have been committed. Persisting the
 * progress in the same atomic batch as the trie nodes is recommended; migration chunks are
 * idempotent, so replaying the last chunk after a crash between the trie commit and the progress
 * save converges to the same state.
 */
public interface MigrationProgressStore {

  /**
   * Loads the last saved progress.
   *
   * @return The progress, or empty if the migration has never run.
   */
  Optional<MigrationProgress> load();

  /**
   * Saves the given progress.
   *
   * @param progress The progress to persist.
   */
  void save(MigrationProgress progress);
}
