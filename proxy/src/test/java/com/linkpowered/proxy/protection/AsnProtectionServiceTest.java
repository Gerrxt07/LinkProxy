/*
 * Copyright (C) 2026 Link Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.linkpowered.proxy.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.maxmind.db.Reader;
import java.io.File;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class AsnProtectionServiceTest {

  @Test
  void extractsCommonAsnFields() throws Exception {
    OptionalInt snakeCase = AsnProtectionService.extractAsn(
        Map.of("autonomous_system_number", 24940));
    OptionalInt asn = AsnProtectionService.extractAsn(Map.of("asn", "AS16276"));

    assertTrue(snakeCase.isPresent());
    assertEquals(24940, snakeCase.getAsInt());
    assertTrue(asn.isPresent());
    assertEquals(16276, asn.getAsInt());
  }

  @Test
  void extractsNestedAsnFields() throws Exception {
    OptionalInt extracted = AsnProtectionService.extractAsn(
        Map.of("traits", Map.of("autonomousSystemNumber", 14061)));

    assertTrue(extracted.isPresent());
    assertEquals(14061, extracted.getAsInt());
  }


  @Test
  void decodesRealMmdbRecordIntoExtractor() throws Exception {
    File database = Path.of("dbip-asn-lite-2026-06.mmdb").toFile();
    if (!database.isFile()) {
      return;
    }

    try (Reader reader = new Reader(database)) {
      Map<String, Object> record = reader.get(InetAddress.getByName("82.144.35.10"), Map.class);
      OptionalInt extracted = AsnProtectionService.extractAsn(record);

      assertTrue(extracted.isPresent());
    }
  }

  @Test
  void returnsEmptyWhenNoAsnIsPresent() throws Exception {
    assertTrue(AsnProtectionService.extractAsn(
        Map.of("country", Map.of("iso_code", "DE"))).isEmpty());
  }
}
