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
package org.hyperledger.besu.ethereum.trie.verkle.proof;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionWitness {
  @JsonProperty("stateDiff")
  private List<StateDiff> stateDiffs;

  @JsonProperty("verkleProof")
  private VerkleProof verkleProof;

  // Getters and setters
  public List<StateDiff> getStateDiffs() {
    return stateDiffs;
  }

  public void setStateDiffs(List<StateDiff> stateDiffs) {
    this.stateDiffs = stateDiffs;
  }

  public VerkleProof getVerkleProof() {
    return verkleProof;
  }

  public void setVerkleProof(VerkleProof verkleProof) {
    this.verkleProof = verkleProof;
  }
}
