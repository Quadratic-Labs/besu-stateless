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

/**
 * Thrown when the target block is reached but the migration enumeration is not exhausted: the
 * protocol parameters guarantee {@code stemsPerBlock * (targetBlock - startBlock) >= totalStems},
 * so this indicates a misconfiguration or a corrupted migration state.
 */
public class TransitionIncompleteException extends IllegalStateException {

  /**
   * Creates the exception.
   *
   * @param blockNumber The block number at which the cutover was attempted.
   * @param remainingStems The number of stem units left to migrate.
   */
  public TransitionIncompleteException(final long blockNumber, final long remainingStems) {
    super(
        "Migration incomplete at target block "
            + blockNumber
            + ": "
            + remainingStems
            + " stem units remaining");
  }
}
