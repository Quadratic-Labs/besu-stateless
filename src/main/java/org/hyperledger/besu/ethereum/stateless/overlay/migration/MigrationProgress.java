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

/**
 * Persistent migration progress: how far into the {@link MigrationSource} enumeration the migration
 * has advanced.
 *
 * @param frontier The number of stem units consumed so far.
 * @param complete Whether the enumeration is exhausted.
 */
public record MigrationProgress(long frontier, boolean complete) {

  /**
   * Progress of a migration that has not started.
   *
   * @return Zero progress.
   */
  public static MigrationProgress zero() {
    return new MigrationProgress(0, false);
  }
}
