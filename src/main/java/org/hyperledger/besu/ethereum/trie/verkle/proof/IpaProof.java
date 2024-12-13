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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.bytes.Bytes;

public class IpaProof {
  @JsonProperty("cl")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private List<Bytes> cl;

  @JsonProperty("cr")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private List<Bytes> cr;

  @JsonProperty("finalEvaluation")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private Bytes finalEvaluation;

  public List<Bytes> getCL() {
    return cl;
  }

  public void setCL(List<Bytes> cl) {
    this.cl = cl;
  }

  public List<Bytes> getCR() {
    return cr;
  }

  public void setCR(List<Bytes> cr) {
    this.cr = cr;
  }

  public Bytes getFinalEvaluation() {
    return finalEvaluation;
  }

  public void setFinalEvaluation(Bytes finalEvaluation) {
    this.finalEvaluation = finalEvaluation;
  }
}
