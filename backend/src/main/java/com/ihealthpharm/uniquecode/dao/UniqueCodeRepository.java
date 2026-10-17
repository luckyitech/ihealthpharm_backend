package com.ihealthpharm.uniquecode.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ihealthpharm.uniquecode.model.UniqueCodeModel;

public interface UniqueCodeRepository extends JpaRepository<UniqueCodeModel, Integer>{

	UniqueCodeModel findByUniqueCodeName(String uniceCodeName);

	/**
	 * Moves a document counter on by one, atomically.
	 *
	 * The read-add-save in UniqueCodeServiceImple cannot do this safely: it has
	 * no transaction, no lock and no @Version, so two callers read the same
	 * number and both hand out the same code. Incrementing inside the UPDATE
	 * leaves the arithmetic to the database, which holds a row lock for the
	 * duration, so a second caller waits and then sees the incremented value.
	 *
	 * Returns the number of rows changed - zero means the counter row is
	 * missing, which the caller should treat as a failure rather than carrying
	 * on with a number nobody reserved.
	 */
	// Deliberately not clearAutomatically: nothing here holds a UniqueCodeModel
	// that could go stale, and clearing would detach everything else loaded in
	// the same transaction - the approval-PIN check just before this one saves
	// a migrated PIN and relies on that entity still being managed.
	@Modifying
	@Query(nativeQuery = true, value =
		"UPDATE generateuniquecode SET UNIQUE_CODE_NUMBER = UNIQUE_CODE_NUMBER + 1, "
		+ "       LAST_UPDATE_TS = NOW() "
		+ "WHERE UNIQUE_CODE_NAME = :uniqueCodeName")
	int incrementUniqueCodeNumber(@Param("uniqueCodeName") String uniqueCodeName);

	/**
	 * The counter as it now stands. Safe to read straight after
	 * {@link #incrementUniqueCodeNumber} within the same transaction, because
	 * that statement still holds the row lock.
	 */
	@Query(nativeQuery = true, value =
		"SELECT UNIQUE_CODE_NUMBER FROM generateuniquecode WHERE UNIQUE_CODE_NAME = :uniqueCodeName")
	Integer findUniqueCodeNumber(@Param("uniqueCodeName") String uniqueCodeName);

}
