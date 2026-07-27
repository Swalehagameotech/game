package com.teenpatti.platform.table;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TableRepository extends MongoRepository<Table, String> {

    Optional<Table> findByInviteCode(String inviteCode);

    List<Table> findByHostIdAndStatusIn(String hostId, List<TableStatus> statuses);

    List<Table> findBySeatedPlayerIdsContainingAndStatusIn(String userId, List<TableStatus> statuses);

    List<Table> findByStatusIn(List<TableStatus> statuses);

    List<Table> findByStatus(TableStatus status);

    long countByStatus(TableStatus status);

    long countByStatusIn(List<TableStatus> statuses);

    Page<Table> findByStatusIn(List<TableStatus> statuses, Pageable pageable);

    Page<Table> findByStatus(TableStatus status, Pageable pageable);

    List<Table> findByTableTypeAndStatusNot(TableType tableType, TableStatus status);

    List<Table> findByTableTypeAndStatus(TableType tableType, TableStatus status);

    @Query("{ 'tableType': 'PUBLIC', 'status': { '$in': ['WAITING', 'COUNTDOWN', 'RUNNING', 'IN_PROGRESS'] }, '$expr': { '$lt': [ { '$size': '$seatedPlayerIds' }, '$maxPlayers' ] } }")
    Page<Table> findAvailablePublicTables(Pageable pageable);

    @Query("{ 'tableType': 'PUBLIC', 'status': { '$in': ['WAITING', 'COUNTDOWN', 'RUNNING', 'IN_PROGRESS'] }, 'bootAmountPaise': ?0, '$expr': { '$lt': [ { '$size': '$seatedPlayerIds' }, '$maxPlayers' ] } }")
    Page<Table> findAvailablePublicTablesByBootAmount(long bootAmountPaise, Pageable pageable);

    @Query("{ 'tableType': 'PUBLIC', 'status': { '$in': ['WAITING', 'COUNTDOWN', 'RUNNING', 'IN_PROGRESS'] }, 'stakeTier': ?0, '$expr': { '$lt': [ { '$size': '$seatedPlayerIds' }, '$maxPlayers' ] } }")
    Page<Table> findAvailablePublicTablesByStakeTier(StakeTier stakeTier, Pageable pageable);
}
