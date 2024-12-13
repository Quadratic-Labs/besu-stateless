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

public class VerkleProof {
  @JsonProperty("depthExtensionPresent")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private List<Bytes> depthsExtensionPresent;

  @JsonProperty("otherStems")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private List<Bytes> otherStems;

  @JsonProperty("commitmentsByPath")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private List<Bytes> commitmentsByPath;

  @JsonProperty("d")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private Bytes d;

  @JsonProperty("ipaProof")
  private IpaProof ipaProof;

  public List<Bytes> getDepthsExtensionPresent() {
    return depthsExtensionPresent;
  }

  public void setDepthsExtensionPresent(List<Bytes> depthsExtensionPresent) {
    this.depthsExtensionPresent = depthsExtensionPresent;
  }

  public List<Bytes> getOtherStems() {
    return otherStems;
  }

  public void setOtherStems(List<Bytes> otherStems) {
    this.otherStems = otherStems;
  }

  public List<Bytes> getCommitmentsByPath() {
    return commitmentsByPath;
  }

  public void setCommitmentsByPath(List<Bytes> commitmentsByPath) {
    this.commitmentsByPath = commitmentsByPath;
  }

  public Bytes getD() {
    return d;
  }

  public void setD(Bytes d) {
    this.d = d;
  }

  public IpaProof getIpaProof() {
    return ipaProof;
  }

  public void setProof(IpaProof ipaProof) {
    this.ipaProof = ipaProof;
  }
}
