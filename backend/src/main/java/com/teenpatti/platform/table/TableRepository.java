package com.teenpatti.platform.table;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableRepository extends MongoRepository<Table, String> {

    Optional<Table> findByInviteCode(String inviteCode);

    @Query("{ 'tableType': 'PUBLIC', 'status': { '$in': ['WAITING', 'IN_PROGRESS'] }, '$expr': { '$lt': [ { '$size': '$seatedPlayerIds' }, '$maxPlayers' ] } }")
    Page<Table> findAvailablePublicTables(Pageable pageable);

    @Query("{ 'tableType': 'PUBLIC', 'status': { '$in': ['WAITING', 'IN_PROGRESS'] }, 'stakeTier': ?0, '$expr': { '$lt': [ { '$size': '$seatedPlayerIds' }, '$maxPlayers' ] } }")
    Page<Table> findAvailablePublicTablesByStakeTier(StakeTier stakeTier, Pageable pageable);
}
