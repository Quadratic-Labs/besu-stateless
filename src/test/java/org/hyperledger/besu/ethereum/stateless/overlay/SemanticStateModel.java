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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Plain-maps oracle of semantic world state. Implements {@link WorldStateWriter} so the same ops
 * can be applied to it and to an overlay under test; {@link ReferenceBinTrieBuilder} turns it into
 * a ground-truth binary trie root.
 */
public class SemanticStateModel implements WorldStateWriter {

  /** Header fields of a modelled account. */
  public record Account(long nonce, UInt256 balance) {}

  public final Map<Bytes, Account> accounts = new TreeMap<>();
  public final Map<Bytes, TreeMap<UInt256, UInt256>> storage = new HashMap<>();
  public final Map<Bytes, Bytes> code = new HashMap<>();

  @Override
  public void putAccount(Bytes address, long nonce, UInt256 balance) {
    accounts.put(address, new Account(nonce, balance));
  }

  @Override
  public void putCode(Bytes address, Bytes newCode) {
    if (newCode.isEmpty()) {
      code.remove(address);
    } else {
      code.put(address, newCode);
    }
  }

  @Override
  public void putStorage(Bytes address, UInt256 slot, UInt256 value) {
    if (value.isZero()) {
      TreeMap<UInt256, UInt256> slots = storage.get(address);
      if (slots != null) {
        slots.remove(slot);
        if (slots.isEmpty()) {
          storage.remove(address);
        }
      }
    } else {
      storage.computeIfAbsent(address, a -> new TreeMap<>()).put(slot, value);
    }
  }

  @Override
  public void removeAccount(Bytes address, List<UInt256> knownStorageKeys, Bytes currentCode) {
    accounts.remove(address);
    storage.remove(address);
    code.remove(address);
  }

  /** Returns the code of an account, empty bytes if none. */
  public Bytes codeOf(Bytes address) {
    return code.getOrDefault(address, Bytes.EMPTY);
  }

  /** Returns the storage of an account (possibly empty), keyed and ordered by slot. */
  public SortedMap<UInt256, UInt256> storageOf(Bytes address) {
    return storage.getOrDefault(address, new TreeMap<>());
  }

  /** Deep copy, used to freeze the fork-block snapshot. */
  public SemanticStateModel copy() {
    SemanticStateModel copy = new SemanticStateModel();
    copy.accounts.putAll(accounts);
    storage.forEach((address, slots) -> copy.storage.put(address, new TreeMap<>(slots)));
    copy.code.putAll(code);
    return copy;
  }
}
