/*
 * SonarQube
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.core.issue;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldDiffsTest {

  FieldDiffs diffs = new FieldDiffs();

  @Test
  public void diffs_should_be_empty_by_default() {
    assertThat(diffs.diffs()).isEmpty();
  }

  @Test
  public void test_diff() {
    diffs.setDiff("severity", "BLOCKER", "INFO");
    diffs.setDiff("resolution", "OPEN", "FIXED");

    assertThat(diffs.diffs()).hasSize(2);

    FieldDiffs.Diff diff = diffs.diffs().get("severity");
    assertThat(diff.oldValue()).isEqualTo("BLOCKER");
    assertThat(diff.newValue()).isEqualTo("INFO");

    diff = diffs.diffs().get("resolution");
    assertThat(diff.oldValue()).isEqualTo("OPEN");
    assertThat(diff.newValue()).isEqualTo("FIXED");
  }

  @Test
  public void diff_with_long_values() {
    diffs.setDiff("technicalDebt", 50l, "100");

    FieldDiffs.Diff diff = diffs.diffs().get("technicalDebt");
    assertThat(diff.oldValueLong()).isEqualTo(50l);
    assertThat(diff.newValueLong()).isEqualTo(100l);
  }

  @Test
  public void diff_with_empty_long_values() {
    diffs.setDiff("technicalDebt", null, "");

    FieldDiffs.Diff diff = diffs.diffs().get("technicalDebt");
    assertThat(diff.oldValueLong()).isNull();
    assertThat(diff.newValueLong()).isNull();
  }

  @Test
  public void test_diff_by_key() {
    diffs.setDiff("severity", "BLOCKER", "INFO");
    diffs.setDiff("resolution", "OPEN", "FIXED");

    assertThat(diffs.diffs()).hasSize(2);

    FieldDiffs.Diff diff = diffs.diffs().get("severity");
    assertThat(diff.oldValue()).isEqualTo("BLOCKER");
    assertThat(diff.newValue()).isEqualTo("INFO");

    diff = diffs.diffs().get("resolution");
    assertThat(diff.oldValue()).isEqualTo("OPEN");
    assertThat(diff.newValue()).isEqualTo("FIXED");
  }

  @Test
  public void should_keep_old_value() {
    diffs.setDiff("severity", "BLOCKER", "INFO");
    diffs.setDiff("severity", null, "MAJOR");
    FieldDiffs.Diff diff = diffs.diffs().get("severity");
    assertThat(diff.oldValue()).isEqualTo("BLOCKER");
    assertThat(diff.newValue()).isEqualTo("MAJOR");
  }

  @Test
  public void test_toString() {
    diffs.setDiff("severity", "BLOCKER", "INFO");
    diffs.setDiff("resolution", "OPEN", "FIXED");

    assertThat(diffs.toString()).isEqualTo("severity=BLOCKER|INFO,resolution=OPEN|FIXED");
  }

  @Test
  public void test_toString_with_null_values() {
    diffs.setDiff("severity", null, "INFO");
    diffs.setDiff("assignee", "user1", null);

    assertThat(diffs.toString()).isEqualTo("severity=INFO,assignee=user1|");
  }

  @Test
  public void test_parse() {
    diffs = FieldDiffs.parse("severity=BLOCKER|INFO,resolution=OPEN|FIXED,donut=|new,gambas=miam,acme=old|");
    assertThat(diffs.diffs()).hasSize(5);

    FieldDiffs.Diff diff = diffs.diffs().get("severity");
    assertThat(diff.oldValue()).isEqualTo("BLOCKER");
    assertThat(diff.newValue()).isEqualTo("INFO");

    diff = diffs.diffs().get("resolution");
    assertThat(diff.oldValue()).isEqualTo("OPEN");
    assertThat(diff.newValue()).isEqualTo("FIXED");

    diff = diffs.diffs().get("donut");
    assertThat(diff.oldValue()).isEqualTo("");
    assertThat(diff.newValue()).isEqualTo("new");

    diff = diffs.diffs().get("gambas");
    assertThat(diff.oldValue()).isEqualTo("");
    assertThat(diff.newValue()).isEqualTo("miam");

    diff = diffs.diffs().get("acme");
    assertThat(diff.oldValue()).isEqualTo("old");
    assertThat(diff.newValue()).isEqualTo("");
  }

  @Test
  public void test_parse_empty_values() {
    diffs = FieldDiffs.parse("severity=INFO,resolution=");
    assertThat(diffs.diffs()).hasSize(2);

    FieldDiffs.Diff diff = diffs.diffs().get("severity");
    assertThat(diff.oldValue()).isEqualTo("");
    assertThat(diff.newValue()).isEqualTo("INFO");

    diff = diffs.diffs().get("resolution");
    assertThat(diff.oldValue()).isEqualTo("");
    assertThat(diff.newValue()).isEqualTo("");
  }

  @Test
  public void test_parse_null() {
    diffs = FieldDiffs.parse(null);
    assertThat(diffs.diffs()).isEmpty();
  }

  @Test
  public void test_parse_empty() {
    diffs = FieldDiffs.parse("");
    assertThat(diffs.diffs()).isEmpty();
  }
}
