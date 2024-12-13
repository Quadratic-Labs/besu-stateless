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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.tuweni.bytes.Bytes;

class SuffixDiff {
  @JsonProperty("suffix")
  private byte suffix;

  @JsonProperty("current_value")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private Bytes currentValue;

  @JsonProperty("new_value")
  @JsonDeserialize(using = BytesDeserializer.class)
  @JsonSerialize(using = BytesSerializer.class)
  private Bytes newValue;

  // Getters and setters
  public byte getSuffix() {
    return suffix;
  }

  public void setSuffix(byte suffix) {
    this.suffix = suffix;
  }

  public Bytes getCurrentValue() {
    return currentValue;
  }

  public void setCurrentValue(Bytes currentValue) {
    this.currentValue = currentValue;
  }

  public Bytes getNewValue() {
    return newValue;
  }

  public void setNewValue(Bytes newValue) {
    this.newValue = newValue;
  }
}
