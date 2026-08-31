package com.ihealthpharm.finance.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ihealthpharm.masters.model.EmployeeAccessModel;

/**
 * Resolves whether a signed-in user holds a named permission.
 *
 * Looks the permission up by name rather than by id: the ids in pharma_access
 * are auto-generated, so the same permission carries different numbers here and
 * in production and an id would only be correct on the machine it was read
 * from. Matching on the name makes the check hold on both.
 */
@Repository
public interface CreditSettlementAccessRepository extends JpaRepository<EmployeeAccessModel, Integer> {

	/**
	 * How many active grants of the named permission the user holds.
	 *
	 * Joined from the user name rather than an employee id so a caller cannot
	 * pass someone else's id, and written natively because employee_access
	 * stores the permission as a plain id with no mapped relation to join over.
	 *
	 * Returns the count rather than a boolean condition: MySQL yields a
	 * comparison as an integer, which cannot be read back as a Boolean. The
	 * count is also tolerant of the duplicate grants already present in
	 * employee_access, where some employees hold the same permission twice.
	 */
	@Query(nativeQuery = true, value =
			"SELECT COUNT(*) FROM employee_access ea "
			+ "JOIN pharma_access pa ON pa.PHARMA_ACCESS_ID = ea.PHARMA_ACCESS_ID "
			+ "JOIN employee_credentials ec ON ec.EMPLOYEE_ID = ea.EMPLOYEE_ID "
			+ "WHERE ec.USER_NM = :userName "
			+ "AND pa.ACCESS_NAME = :accessName "
			+ "AND ea.ACTIVE_S = 'Y' AND pa.ACTIVE_S = 'Y'")
	long countPermission(@Param("userName") String userName, @Param("accessName") String accessName);

}
