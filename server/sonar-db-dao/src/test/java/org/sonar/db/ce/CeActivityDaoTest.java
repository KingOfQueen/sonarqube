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
package org.sonar.db.ce;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.internal.TestSystem2;
import org.sonar.core.util.CloseableIterator;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.Pagination;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.Pagination.forPage;
import static org.sonar.db.ce.CeActivityDto.Status.FAILED;
import static org.sonar.db.ce.CeActivityDto.Status.SUCCESS;
import static org.sonar.db.ce.CeQueueDto.Status.PENDING;
import static org.sonar.db.ce.CeQueueTesting.makeInProgress;
import static org.sonar.db.ce.CeTaskTypes.REPORT;

public class CeActivityDaoTest {

  private static final String MAINCOMPONENT_1 = randomAlphabetic(12);
  private static final String MAINCOMPONENT_2 = randomAlphabetic(13);
  private static final String COMPONENT_1 = randomAlphabetic(14);

  private TestSystem2 system2 = new TestSystem2().setNow(1_450_000_000_000L);

  @Rule
  public DbTester db = DbTester.create(system2);

  private DbSession dbSession = db.getSession();
  private CeActivityDao underTest = new CeActivityDao(system2);

  @Test
  public void test_insert() {
    CeActivityDto inserted = insert("TASK_1", REPORT, COMPONENT_1, MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);

    Optional<CeActivityDto> saved = underTest.selectByUuid(db.getSession(), "TASK_1");
    assertThat(saved).isPresent();
    CeActivityDto dto = saved.get();
    assertThat(dto.getUuid()).isEqualTo("TASK_1");
    assertThat(dto.getMainComponentUuid()).isEqualTo(MAINCOMPONENT_1);
    assertThat(dto.getComponentUuid()).isEqualTo(COMPONENT_1);
    assertThat(dto.getStatus()).isEqualTo(CeActivityDto.Status.SUCCESS);
    assertThat(dto.getSubmitterUuid()).isEqualTo("submitter uuid");
    assertThat(dto.getSubmittedAt()).isEqualTo(1_450_000_000_000L);
    assertThat(dto.getWorkerUuid()).isEqualTo("worker uuid");
    assertThat(dto.getIsLast()).isTrue();
    assertThat(dto.getMainIsLast()).isTrue();
    assertThat(dto.getIsLastKey()).isEqualTo("REPORT" + COMPONENT_1);
    assertThat(dto.getMainIsLastKey()).isEqualTo("REPORT" + MAINCOMPONENT_1);
    assertThat(dto.getCreatedAt()).isEqualTo(1_450_000_000_000L);
    assertThat(dto.getStartedAt()).isEqualTo(1_500_000_000_000L);
    assertThat(dto.getExecutedAt()).isEqualTo(1_500_000_000_500L);
    assertThat(dto.getExecutionTimeMs()).isEqualTo(500L);
    assertThat(dto.getAnalysisUuid()).isEqualTo(inserted.getAnalysisUuid());
    assertThat(dto.toString()).isNotEmpty();
    assertThat(dto.getErrorMessage()).isNull();
    assertThat(dto.getErrorStacktrace()).isNull();
    assertThat(dto.getErrorType()).isNull();
    assertThat(dto.isHasScannerContext()).isFalse();
  }

  @Test
  public void test_insert_of_errorMessage_of_1_000_chars() {
    CeActivityDto dto = createActivityDto("TASK_1", REPORT, COMPONENT_1, MAINCOMPONENT_1, CeActivityDto.Status.FAILED)
      .setErrorMessage(Strings.repeat("x", 1_000));
    underTest.insert(db.getSession(), dto);

    Optional<CeActivityDto> saved = underTest.selectByUuid(db.getSession(), "TASK_1");
    assertThat(saved.get().getErrorMessage()).isEqualTo(dto.getErrorMessage());
  }

  @Test
  public void test_insert_of_errorMessage_of_1_001_chars_is_truncated_to_1000() {
    String expected = Strings.repeat("x", 1_000);
    CeActivityDto dto = createActivityDto("TASK_1", REPORT, COMPONENT_1, MAINCOMPONENT_1, CeActivityDto.Status.FAILED)
      .setErrorMessage(expected + "y");
    underTest.insert(db.getSession(), dto);

    Optional<CeActivityDto> saved = underTest.selectByUuid(db.getSession(), "TASK_1");
    assertThat(saved.get().getErrorMessage()).isEqualTo(expected);
  }

  @Test
  public void test_insert_error_message_and_stacktrace() {
    CeActivityDto dto = createActivityDto("TASK_1", REPORT, COMPONENT_1, MAINCOMPONENT_1, CeActivityDto.Status.FAILED)
      .setErrorStacktrace("error stack");
    underTest.insert(db.getSession(), dto);

    Optional<CeActivityDto> saved = underTest.selectByUuid(db.getSession(), "TASK_1");
    CeActivityDto read = saved.get();
    assertThat(read.getErrorMessage()).isEqualTo(dto.getErrorMessage());
    assertThat(read.getErrorStacktrace()).isEqualTo(dto.getErrorStacktrace());
    assertThat(read.getErrorType()).isNotNull().isEqualTo(dto.getErrorType());
  }

  @Test
  public void test_insert_error_message_only() {
    CeActivityDto dto = createActivityDto("TASK_1", REPORT, COMPONENT_1, MAINCOMPONENT_1, CeActivityDto.Status.FAILED);
    underTest.insert(db.getSession(), dto);

    Optional<CeActivityDto> saved = underTest.selectByUuid(db.getSession(), "TASK_1");
    CeActivityDto read = saved.get();
    assertThat(read.getErrorMessage()).isEqualTo(read.getErrorMessage());
    assertThat(read.getErrorStacktrace()).isNull();
  }

  @Test
  public void insert_must_set_relevant_is_last_field() {
    // only a single task on MAINCOMPONENT_1 -> is_last=true
    insert("TASK_1", REPORT, MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_1").get().getIsLast()).isTrue();

    // only a single task on MAINCOMPONENT_2 -> is_last=true
    insert("TASK_2", REPORT, MAINCOMPONENT_2, CeActivityDto.Status.SUCCESS);
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_2").get().getIsLast()).isTrue();

    // two tasks on MAINCOMPONENT_1, the most recent one is TASK_3
    insert("TASK_3", REPORT, MAINCOMPONENT_1, FAILED);
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_1").get().getIsLast()).isFalse();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_2").get().getIsLast()).isTrue();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_3").get().getIsLast()).isTrue();

    // inserting a cancelled task does not change the last task
    insert("TASK_4", REPORT, MAINCOMPONENT_1, CeActivityDto.Status.CANCELED);
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_1").get().getIsLast()).isFalse();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_2").get().getIsLast()).isTrue();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_3").get().getIsLast()).isTrue();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_4").get().getIsLast()).isFalse();
  }

  @Test
  public void test_selectByQuery() {
    insert("TASK_1", REPORT, MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);
    insert("TASK_2", REPORT, MAINCOMPONENT_1, FAILED);
    insert("TASK_3", REPORT, MAINCOMPONENT_2, CeActivityDto.Status.SUCCESS);
    insert("TASK_4", "views", null, CeActivityDto.Status.SUCCESS);

    // no filters
    CeTaskQuery query = new CeTaskQuery().setStatuses(Collections.emptyList());
    List<CeActivityDto> dtos = underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(10));
    assertThat(dtos).extracting("uuid").containsExactly("TASK_4", "TASK_3", "TASK_2", "TASK_1");

    // select by component uuid
    query = new CeTaskQuery().setMainComponentUuid(MAINCOMPONENT_1);
    dtos = underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(100));
    assertThat(dtos).extracting("uuid").containsExactly("TASK_2", "TASK_1");

    // select by status
    query = new CeTaskQuery().setStatuses(singletonList(CeActivityDto.Status.SUCCESS.name()));
    dtos = underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(100));
    assertThat(dtos).extracting("uuid").containsExactly("TASK_4", "TASK_3", "TASK_1");

    // select by type
    query = new CeTaskQuery().setType(REPORT);
    dtos = underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(100));
    assertThat(dtos).extracting("uuid").containsExactly("TASK_3", "TASK_2", "TASK_1");
    query = new CeTaskQuery().setType("views");
    dtos = underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(100));
    assertThat(dtos).extracting("uuid").containsExactly("TASK_4");

    // select by multiple conditions
    query = new CeTaskQuery().setType(REPORT).setOnlyCurrents(true).setMainComponentUuid(MAINCOMPONENT_1);
    dtos = underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(100));
    assertThat(dtos).extracting("uuid").containsExactly("TASK_2");
  }

  @Test
  public void selectByQuery_does_not_populate_errorStacktrace_field() {
    insert("TASK_1", REPORT, MAINCOMPONENT_1, FAILED);
    underTest.insert(db.getSession(), createActivityDto("TASK_2", REPORT, COMPONENT_1, MAINCOMPONENT_1, FAILED).setErrorStacktrace("some stack"));

    List<CeActivityDto> dtos = underTest.selectByQuery(db.getSession(), new CeTaskQuery().setMainComponentUuid(MAINCOMPONENT_1), forPage(1).andSize(100));

    assertThat(dtos)
      .hasSize(2)
      .extracting("errorStacktrace").containsOnly((String) null);
  }

  @Test
  public void selectByQuery_populates_hasScannerContext_flag() {
    insert("TASK_1", REPORT, MAINCOMPONENT_1, SUCCESS);
    CeActivityDto dto2 = insert("TASK_2", REPORT, MAINCOMPONENT_2, SUCCESS);
    insertScannerContext(dto2.getUuid());

    CeActivityDto dto = underTest.selectByQuery(db.getSession(), new CeTaskQuery().setMainComponentUuid(MAINCOMPONENT_1), forPage(1).andSize(100)).iterator().next();
    assertThat(dto.isHasScannerContext()).isFalse();
    dto = underTest.selectByQuery(db.getSession(), new CeTaskQuery().setMainComponentUuid(MAINCOMPONENT_2), forPage(1).andSize(100)).iterator().next();
    assertThat(dto.isHasScannerContext()).isTrue();
  }

  @Test
  public void selectByQuery_is_paginated_and_return_results_sorted_from_last_to_first() {
    insert("TASK_1", REPORT, MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);
    insert("TASK_2", REPORT, MAINCOMPONENT_1, CeActivityDto.Status.FAILED);
    insert("TASK_3", REPORT, MAINCOMPONENT_2, CeActivityDto.Status.SUCCESS);
    insert("TASK_4", "views", null, CeActivityDto.Status.SUCCESS);

    assertThat(selectPageOfUuids(forPage(1).andSize(1))).containsExactly("TASK_4");
    assertThat(selectPageOfUuids(forPage(2).andSize(1))).containsExactly("TASK_3");
    assertThat(selectPageOfUuids(forPage(1).andSize(3))).containsExactly("TASK_4", "TASK_3", "TASK_2");
    assertThat(selectPageOfUuids(forPage(1).andSize(4))).containsExactly("TASK_4", "TASK_3", "TASK_2", "TASK_1");
    assertThat(selectPageOfUuids(forPage(2).andSize(3))).containsExactly("TASK_1");
    assertThat(selectPageOfUuids(forPage(1).andSize(100))).containsExactly("TASK_4", "TASK_3", "TASK_2", "TASK_1");
    assertThat(selectPageOfUuids(forPage(5).andSize(2))).isEmpty();
  }

  @Test
  public void selectByQuery_no_results_if_shortcircuited_by_component_uuids() {
    insert("TASK_1", REPORT, MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);

    CeTaskQuery query = new CeTaskQuery();
    query.setMainComponentUuids(Collections.emptyList());
    assertThat(underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(1))).isEmpty();
  }

  @Test
  public void select_and_count_by_date() {
    insertWithDates("UUID1", 1_450_000_000_000L, 1_470_000_000_000L);
    insertWithDates("UUID2", 1_460_000_000_000L, 1_480_000_000_000L);

    // search by min submitted date
    CeTaskQuery query = new CeTaskQuery().setMinSubmittedAt(1_455_000_000_000L);
    assertThat(underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(5))).extracting("uuid").containsOnly("UUID2");

    // search by max executed date
    query = new CeTaskQuery().setMaxExecutedAt(1_475_000_000_000L);
    assertThat(underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(5))).extracting("uuid").containsOnly("UUID1");

    // search by both dates
    query = new CeTaskQuery()
      .setMinSubmittedAt(1_400_000_000_000L)
      .setMaxExecutedAt(1_475_000_000_000L);
    assertThat(underTest.selectByQuery(db.getSession(), query, forPage(1).andSize(5))).extracting("uuid").containsOnly("UUID1");

  }

  private void insertWithDates(String uuid, long submittedAt, long executedAt) {
    CeQueueDto queueDto = new CeQueueDto();
    queueDto.setUuid(uuid);
    queueDto.setTaskType("fake");
    CeActivityDto dto = new CeActivityDto(queueDto);
    dto.setStatus(CeActivityDto.Status.SUCCESS);
    dto.setSubmittedAt(submittedAt);
    dto.setExecutedAt(executedAt);
    underTest.insert(db.getSession(), dto);
  }

  @Test
  public void selectOlderThan() {
    insertWithCreationDate("TASK_1", 1_450_000_000_000L);
    insertWithCreationDate("TASK_2", 1_460_000_000_000L);
    insertWithCreationDate("TASK_3", 1_470_000_000_000L);

    List<CeActivityDto> dtos = underTest.selectOlderThan(db.getSession(), 1_465_000_000_000L);
    assertThat(dtos).extracting("uuid").containsOnly("TASK_1", "TASK_2");
  }

  @Test
  public void selectOlder_populates_hasScannerContext_flag() {
    insertWithCreationDate("TASK_1", 1_450_000_000_000L);
    CeActivityDto dto2 = insertWithCreationDate("TASK_2", 1_450_000_000_000L);
    insertScannerContext(dto2.getUuid());

    List<CeActivityDto> dtos = underTest.selectOlderThan(db.getSession(), 1_465_000_000_000L);
    assertThat(dtos).hasSize(2);
    dtos.forEach((dto) -> assertThat(dto.isHasScannerContext()).isEqualTo(dto.getUuid().equals("TASK_2")));
  }

  @Test
  public void selectOlderThan_does_not_populate_errorStacktrace() {
    insert("TASK_1", REPORT, MAINCOMPONENT_1, FAILED);
    underTest.insert(db.getSession(), createActivityDto("TASK_2", REPORT, COMPONENT_1, MAINCOMPONENT_1, FAILED).setErrorStacktrace("some stack"));

    List<CeActivityDto> dtos = underTest.selectOlderThan(db.getSession(), system2.now() + 1_000_000L);

    assertThat(dtos)
      .hasSize(2)
      .extracting("errorStacktrace").containsOnly((String) null);
  }

  @Test
  public void deleteByUuids() {
    insert("TASK_1", "REPORT", MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);
    insert("TASK_2", "REPORT", MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);
    insert("TASK_3", "REPORT", MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);

    underTest.deleteByUuids(db.getSession(), ImmutableSet.of("TASK_1", "TASK_3"));
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_1").isPresent()).isFalse();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_2")).isPresent();
    assertThat(underTest.selectByUuid(db.getSession(), "TASK_3").isPresent()).isFalse();
  }

  @Test
  public void deleteByUuids_does_nothing_if_uuid_does_not_exist() {
    insert("TASK_1", "REPORT", MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);

    // must not fail
    underTest.deleteByUuids(db.getSession(), singleton("TASK_2"));

    assertThat(underTest.selectByUuid(db.getSession(), "TASK_1")).isPresent();
  }

  @Test
  public void count_last_by_status_and_main_component_uuid() {
    insert("TASK_1", CeTaskTypes.REPORT, MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);
    // component 2
    insert("TASK_2", CeTaskTypes.REPORT, MAINCOMPONENT_2, CeActivityDto.Status.SUCCESS);
    // status failed
    insert("TASK_3", CeTaskTypes.REPORT, MAINCOMPONENT_1, CeActivityDto.Status.FAILED);
    // status canceled
    insert("TASK_4", CeTaskTypes.REPORT, MAINCOMPONENT_1, CeActivityDto.Status.CANCELED);
    insert("TASK_5", CeTaskTypes.REPORT, MAINCOMPONENT_1, CeActivityDto.Status.SUCCESS);
    db.commit();

    assertThat(underTest.countLastByStatusAndMainComponentUuid(dbSession, SUCCESS, MAINCOMPONENT_1)).isEqualTo(1);
    assertThat(underTest.countLastByStatusAndMainComponentUuid(dbSession, SUCCESS, null)).isEqualTo(2);
  }

  private CeActivityDto insert(String uuid, String type, @Nullable String mainComponentUuid, CeActivityDto.Status status) {
    return insert(uuid, type, mainComponentUuid, mainComponentUuid, status);
  }

  private CeActivityDto insert(String uuid, String type, String componentUuid, @Nullable String mainComponentUuid, CeActivityDto.Status status) {
    CeActivityDto dto = createActivityDto(uuid, type, componentUuid, mainComponentUuid, status);
    underTest.insert(db.getSession(), dto);
    return dto;
  }

  private CeActivityDto createActivityDto(String uuid, String type, @Nullable String componentUuid, @Nullable String mainComponentUuid, CeActivityDto.Status status) {
    CeQueueDto creating = new CeQueueDto();
    creating.setUuid(uuid);
    creating.setStatus(PENDING);
    creating.setTaskType(type);
    creating.setComponentUuid(componentUuid);
    creating.setMainComponentUuid(mainComponentUuid);
    creating.setSubmitterUuid("submitter uuid");
    creating.setCreatedAt(1_300_000_000_000L);

    db.getDbClient().ceQueueDao().insert(dbSession, creating);
    makeInProgress(dbSession, "worker uuid", 1_400_000_000_000L, creating);

    CeQueueDto ceQueueDto = db.getDbClient().ceQueueDao().selectByUuid(dbSession, uuid).get();

    CeActivityDto dto = new CeActivityDto(ceQueueDto);
    dto.setStatus(status);
    dto.setStartedAt(1_500_000_000_000L);
    dto.setExecutedAt(1_500_000_000_500L);
    dto.setExecutionTimeMs(500L);
    dto.setAnalysisUuid(uuid + "_2");
    if (status == FAILED) {
      dto.setErrorMessage("error msg for " + uuid);
      dto.setErrorType("anErrorType");
    }
    return dto;
  }

  private CeActivityDto insertWithCreationDate(String uuid, long date) {
    CeQueueDto queueDto = new CeQueueDto();
    queueDto.setUuid(uuid);
    queueDto.setTaskType("fake");

    CeActivityDto dto = new CeActivityDto(queueDto);
    dto.setStatus(CeActivityDto.Status.SUCCESS);
    dto.setAnalysisUuid(uuid + "_AA");
    system2.setNow(date);
    underTest.insert(db.getSession(), dto);
    return dto;
  }

  private void insertScannerContext(String taskUuid) {
    db.getDbClient().ceScannerContextDao().insert(dbSession, taskUuid, CloseableIterator.from(singletonList("scanner context of " + taskUuid).iterator()));
    dbSession.commit();
  }

  private List<String> selectPageOfUuids(Pagination pagination) {
    return underTest.selectByQuery(db.getSession(), new CeTaskQuery(), pagination).stream()
      .map(CeActivityToUuid.INSTANCE::apply)
      .collect(MoreCollectors.toList());
  }

  private enum CeActivityToUuid implements Function<CeActivityDto, String> {
    INSTANCE;

    @Override
    public String apply(@Nonnull CeActivityDto input) {
      return input.getUuid();
    }
  }
}
