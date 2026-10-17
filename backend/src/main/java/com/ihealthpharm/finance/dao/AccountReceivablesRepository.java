package com.ihealthpharm.finance.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import javax.transaction.Transactional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ihealthpharm.finance.dto.AccRecievablesCustomerDTO;
import com.ihealthpharm.finance.model.AccountReceivablesModel;
import com.ihealthpharm.sales.model.SalesModel;

@Repository
public interface AccountReceivablesRepository extends JpaRepository<AccountReceivablesModel,Integer>
{
	List<AccountReceivablesModel> findAll();

	/**
	 * Total received against a bill across its Sales Billing receivables.
	 *
	 * Used to rebuild the bill's balance from the receivables rather than
	 * adjusting it by the amount just applied, so the figure cannot drift or
	 * be counted twice.
	 *
	 * Matches the bill the same way the open-bill queries name it - see
	 * {@link ReceivableSql#BILL_CODE_MATCH}. Matching on BILL_NO alone missed
	 * the 436 settleable rows that keep their code in SOURCE_REF, so settling
	 * one of those summed nothing and rebuilt its bill as wholly unpaid,
	 * discarding the payments already recorded against it.
	 */
	@Query(nativeQuery = true, value =
		"SELECT COALESCE(SUM(ar.AMOUNT_RECEIVED),0) FROM account_receivables ar "
		+ "WHERE " + ReceivableSql.BILL_CODE_MATCH
		+ ReceivableSql.RECEIVED_PREDICATE)
	BigDecimal sumReceivedForBill(@Param("billCode") String billCode);
	List<AccountReceivablesModel> findAllByOrderByLastUpdateTimestampDesc();
	 
	@Query("select i from sales i inner join customer c on i.customerModel.customerId=c.customerId where i.customerModel.customerId=:customerId")
	List<SalesModel> getAllCustomersByCustomerId(@Param ("customerId")Integer customerId);
	
	@Query("select i from sales i inner join customer_insurance d on i.customerInsuranceModel.customerInsuranceId=d.customerInsuranceId where d.insuranceModel.insurancePolicyId=:customersId and i.activeS='Y'")
	List<SalesModel> getAllBillsByCustomerId(@Param ("customersId")Integer customersId);
	
	@Query("select s from account_receivables s where s.source=:billId")
	List<AccountReceivablesModel> getAccountRecievablesBillId( @Param ("billId")Integer billId);
	
	@Query("select s from sales s where s.billCode=:billCode")
	List<SalesModel> getSalesBasedOnSalesSearch(@Param("billCode")String billCode);
	
	@Query("select ac from account_receivables ac where ac.activeS='Y' group by ac.customerName")
	List<AccountReceivablesModel> getAllAccountPayables();
	
	@Query("select ac from account_receivables ac where ac.customerName=:customerName and ac.activeS='Y' ")
	List<AccountReceivablesModel> getAllCustomersBasedOnName(@Param("customerName")String customerName);
	
	//Account Receivables
		@Query("SELECT distinct ar.receiptNumber from account_receivables ar order by ar.receiptNumber")
		List<String> findAllReceiptNoINAR();

		@Query("select ar.receiptNumber from account_receivables ar where ar.receiptNumber like :RecNo%")
		List<String> findReceiptNoBySearch(@Param("RecNo") String RecNo);
		
		@Query("SELECT distinct ar.customerName from account_receivables ar order by ar.customerName")
		List<String> findAllCustomersNamesINAR();
		
		@Query("select distinct ar.customerName from account_receivables ar where ar.customerName like :searchTerm%")
		List<String> findCustomersNamesBySearch(@Param("searchTerm") String searchTerm);
		
		
		@Query("select ar from account_receivables ar where ar.customerName like :customerName% and ar.activeS='Y' group by ar.customerName")
		List<AccountReceivablesModel> getAllRecievablesCustNames(@Param ("customerName") String customerName);
		
		@Query("select ar from account_receivables ar where ar.SourceRef like  :billCode% and ar.customerName=:customerName")
		List<AccountReceivablesModel> getAllAccRecievablesBySearchBillCode(@Param("billCode")String billCode,@Param("customerName") String customerName);
		
		// popup searches
		
		@Query("select ac from account_receivables ac where ac.receiptDate BETWEEN :start and :end and  ac.SourceRef like :sourceRef%  and "
				+ " ac.customerName like %:customerName% and ac.paymentStatus=:paymentStatus  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccReceivablesSearchByStatusSearchDate(@Param("start") LocalDate start,
				@Param("end") LocalDate end, @Param("sourceRef") String sourceRef, @Param("customerName") String customerName,@Param("paymentStatus") String paymentStatus, Pageable limit);
		
		@Query("select ac from account_receivables ac where ac.SourceRef like :sourceRef%  and "
				+ " ac.customerName like %:customerName% and ac.receiptDate BETWEEN :start and :end order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievableSearchByStatusSearchDateAndSourceRef(@Param("start") LocalDate start,
				@Param("end") LocalDate end, @Param("sourceRef") String sourceRef, @Param("customerName") String customerName, Pageable limit);
		
		@Query("select ac from account_receivables ac where "
				+ " ac.customerName like %:customerName% and ac.receiptDate BETWEEN :start and :end order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievableSearchByStatusSearchDates(@Param("start") LocalDate start,@Param("end") LocalDate end,
				@Param("customerName") String customerName, Pageable limit);
		
		@Query("select ac from account_receivables ac where ac.paymentStatus=:paymentStatus and"
				+ " ac.customerName like %:customerName% and ac.receiptDate BETWEEN :start and :end order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatusSearchDatesAndStatus(@Param("paymentStatus") String paymentStatus,@Param("start") LocalDate start,
				@Param("end") LocalDate end, @Param("customerName") String customerName, Pageable limit);
		
		@Query("select ac from account_receivables ac where ac.paymentStatus=:paymentStatus and ac.SourceRef like :sourceRef% and  "
				+ " ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRef(
				@Param("paymentStatus") String paymentStatus, @Param("sourceRef") String sourceRef,  @Param("customerName") String customerName, Pageable limit);
		
		@Query("select ac from account_receivables ac where ac.paymentStatus=:paymentStatus and ac.SourceRef like :sourceRef%  and  "
				+ "ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatusSearchStatusAndSourceNumber(@Param("paymentStatus") String paymentStatus,
				@Param("sourceRef") String sourceRef,  @Param("customerName") String customerName, Pageable limit);
		
		@Query("select ac from account_receivables ac where  ac.SourceRef like :sourceRef%  and "
				+ " ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchBySourceRef(@Param("sourceRef") String sourceRef,  @Param("customerName") String customerName, Pageable limit);
	
		@Query("select ac from account_receivables ac where ac.paymentStatus=:paymentStatus and  "
				+ " ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatus(	@Param("paymentStatus") String paymentStatus,  @Param("customerName") String customerName, Pageable limit);
		
		
		
		//count
		
		
		@Query("select count(ac) from account_receivables ac where ac.receiptDate BETWEEN :start and :end and  ac.SourceRef like :sourceRef%  and "
				+ " ac.customerName like %:customerName% and ac.paymentStatus=:paymentStatus  order by ac.lastUpdateTimestamp desc")
		Integer findAccReceivablesSearchByStatusSearchDateCount(@Param("start") LocalDate start,
				@Param("end") LocalDate end, @Param("sourceRef") String sourceRef, @Param("customerName") String customerName,@Param("paymentStatus") String paymentStatus);
		
		@Query("select count(ac) from account_receivables ac where ac.SourceRef like :sourceRef%  and "
				+ " ac.customerName like %:customerName% and ac.receiptDate BETWEEN :start and :end order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievableSearchByStatusSearchDateAndSourceRefCount(@Param("start") LocalDate start,
				@Param("end") LocalDate end, @Param("sourceRef") String sourceRef, @Param("customerName") String customerName);
		
		@Query("select count(ac) from account_receivables ac where "
				+ " ac.customerName like %:customerName% and ac.receiptDate BETWEEN :start and :end order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievableSearchByStatusSearchDatesCount(@Param("start") LocalDate start,@Param("end") LocalDate end,
				@Param("customerName") String customerName);
		
		@Query("select count(ac) from account_receivables ac where ac.paymentStatus=:paymentStatus and"
				+ " ac.customerName like %:customerName% and ac.receiptDate BETWEEN :start and :end order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusSearchDatesAndStatusCount(@Param("paymentStatus") String paymentStatus,@Param("start") LocalDate start,
				@Param("end") LocalDate end, @Param("customerName") String customerName);
		
		@Query("select count(ac) from account_receivables ac where ac.paymentStatus=:paymentStatus and ac.SourceRef like :sourceRef%  and  "
				+ " ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRefCount(@Param("paymentStatus") String paymentStatus, @Param("sourceRef") String sourceRef,  @Param("customerName") String customerName );
		
		@Query("select count(ac) from account_receivables ac where ac.paymentStatus=:paymentStatus and ac.SourceRef like :sourceRef%  and  "
				+ " ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusSearchStatusAndSourceNumberCount(@Param("paymentStatus") String paymentStatus,
				@Param("sourceRef") String sourceRef,  @Param("customerName") String customerName );
		
		@Query("select count(ac) from account_receivables ac where  ac.SourceRef like :sourceRef%  and "
				+ " ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchBySourceRefCount(@Param("sourceRef") String sourceRef,  @Param("customerName") String customerName );
	
		@Query("select count(ac) from account_receivables ac where ac.paymentStatus=:paymentStatus and "
				+ "ac.customerName like %:customerName% order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusCount(@Param("paymentStatus") String paymentStatus,@Param("customerName") String customerName);
	
		@Query("select new com.ihealthpharm.finance.dto.AccRecievablesCustomerDTO(ac.accountReceivablesId,ac.customerName)  from account_receivables ac where ac.activeS='Y' group by ac.customerName")
		List<AccRecievablesCustomerDTO> getAllAccountPayablesData();
		
		

		
		// for count in accounts popup
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where ac.receiptDate BETWEEN :start and :end and  ac.SourceRef like :sourceRef%  and "
				+ " s.creditAccountNo=:creditNumber and ac.paymentStatus=:paymentStatus  order by ac.lastUpdateTimestamp desc")
		Integer findAccReceivablesSearchByStatusSearchDateCountForAccounts(@Param("start")LocalDate start,@Param("end") LocalDate end,
			  @Param("sourceRef") String sourceRef,@Param("creditNumber") String creditNumber,@Param("paymentStatus") String paymentStatus);
	
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where ac.receiptDate BETWEEN :start and :end and  ac.SourceRef like :sourceRef%  and "
				+ " s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievableSearchByStatusSearchDateAndSourceRefCountForAccount(@Param("start") LocalDate start,@Param("end") LocalDate end,
			@Param("sourceRef")	String sourceRef,@Param("creditNumber") String creditNumber);
		
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where ac.paymentStatus=:paymentStatus and ac.receiptDate BETWEEN :start and :end and "
		+ " s.creditAccountNo=:creditNumber    order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusSearchDatesAndStatusCountForAccount(@Param("paymentStatus")String paymentStatus,
			 @Param("start") LocalDate start,@Param("end") LocalDate end,@Param("creditNumber") String creditNumber);
		
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where ac.receiptDate BETWEEN :start and :end  "
				+ "and s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievableSearchByStatusSearchDatesCountForAccounts(@Param("start")LocalDate start,@Param("end") LocalDate end,
				@Param("creditNumber")String creditNumber);
		
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where  ac.paymentStatus=:paymentStatus  and  ac.SourceRef like :sourceRef%  and "
				+ " s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRefCountForAcc(@Param("paymentStatus")String paymentStatus,
			@Param("sourceRef")	String sourceRef,@Param("creditNumber") String creditNumber);
		
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where ac.paymentStatus=:paymentStatus and  ac.SourceRef like :sourceRef%  and "
				+ " s.creditAccountNo=:creditNumber   order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusSearchStatusAndSourceNumberCountForAcc(@Param("paymentStatus")String paymentStatus,
				@Param("sourceRef")String sourceRef,@Param("creditNumber") String creditNumber);
		
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where   ac.SourceRef like :sourceRef%  and "
				+ " s.creditAccountNo=:creditNumber   order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchBySourceRefCountForAcc(@Param("sourceRef") String sourceRef,@Param("creditNumber") String creditNumber);
		
		@Query("select count(ac) from account_receivables ac inner join sales s  on ac.SourceRef=s.billCode where ac.paymentStatus=:paymentStatus  and "
				+ " s.creditAccountNo=:creditNumber   order by ac.lastUpdateTimestamp desc")
		Integer findAccRecievablesSearchByStatusCountForAcc(@Param("paymentStatus")String paymentStatus,@Param("creditNumber") String creditNumber);
		
		
		// acc recievables popup searches for master accounts
		
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where ac.receiptDate BETWEEN :start and :end and  ac.SourceRef like :sourceRef%  and "
				+ " ac.paymentStatus=:paymentStatus and  s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccReceivablesSearchByStatusSearchDateForAccount(@Param("start")LocalDate start,
			@Param("end") LocalDate end,@Param("sourceRef") String sourceRef,@Param("creditNumber") String creditNumber,@Param("paymentStatus") String paymentStatus, Pageable limit);
		
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where ac.receiptDate BETWEEN :start and :end and  ac.SourceRef like :sourceRef%  and "
				+ "   s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievableSearchByStatusSearchDateAndSourceRefForAcc(@Param("start")LocalDate start,
			@Param("end") LocalDate end,@Param("sourceRef") String sourceRef,@Param("creditNumber") String creditNumber, Pageable limit);
		
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where ac.paymentStatus=:paymentStatus and ac.receiptDate BETWEEN :start and :end  and "
				+ "  s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatusSearchDatesAndStatusForAcc(@Param("paymentStatus")String paymentStatus,
				@Param("start")LocalDate start,@Param("end") LocalDate end,@Param("creditNumber") String creditNumber, Pageable limit);
	
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where ac.receiptDate BETWEEN :start and :end and "
				+ " s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievableSearchByStatusSearchDatesForAcc(@Param("start")LocalDate start,@Param("end") LocalDate end,
				@Param("creditNumber")String creditNumber, Pageable limit);
		
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where ac.paymentStatus=:paymentStatus and  ac.SourceRef like :sourceRef%  and "
				+ "  s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatusSearchBasedonStatusAndSourceRefForAcc(
				@Param("paymentStatus")String paymentStatus,@Param("sourceRef") String sourceRef,@Param("creditNumber") String creditNumber, Pageable limit);
		
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where ac.paymentStatus=:paymentStatus and  ac.SourceRef like :sourceRef%  and "
				+ "  s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatusSearchStatusAndSourceNumberForAcc(
				@Param("paymentStatus")String paymentStatus,@Param("sourceRef") String sourceRef,@Param("creditNumber") String creditNumber, Pageable limit);
		
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where  ac.SourceRef like :sourceRef%  and "
				+ "  s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchBySourceRefForAcc(@Param("sourceRef")String sourceRef,@Param("creditNumber") String creditNumber,
				Pageable limit);
		
		@Query("select ac from account_receivables ac  inner join sales s  on ac.SourceRef=s.billCode  where  "
				+ " ac.paymentStatus=:paymentStatus and  s.creditAccountNo=:creditNumber  order by ac.lastUpdateTimestamp desc")
		List<AccountReceivablesModel> findAccRecievablesSearchByStatusForAcc(@Param("paymentStatus")String paymentStatus,@Param("creditNumber") String creditNumber,
				Pageable limit);
		
				
		@Query("select s from sales s where s.billCode=:billCode")
		SalesModel getSalesByBillCode(@Param("billCode") String billCode);
		
		@Query("select a from account_receivables a where accountReceivablesId=:accountReceivablesId")
		AccountReceivablesModel getAccRecDataById(@Param("accountReceivablesId")Integer accountReceivablesId);
		
		
		@Query("select ac from account_receivables ac  where ac.SourceRef=:sourceRef and ac.paymentStatus='Pending'")
		AccountReceivablesModel findBySourceRef(@Param("sourceRef")String sourceRef);
		
		@Query("select ac from account_receivables ac  where ac.SourceRef=:sourceRef  and  "
				+ " ac.paymentStatus=:paymentStatus and  ac.sourceType=:sourceType")
		List<AccountReceivablesModel> findAccountReceivablesByBillIdAndStatus(@Param("sourceRef")String sourceRef,@Param("sourceType")String sourceType,@Param("paymentStatus")String paymentStatus);
		
		
		@Transactional
		@Modifying
		@Query("delete from account_receivables ac  where ac.SourceRef=:sourceRef  and  "
				+ " ac.paymentStatus=:paymentStatus and  ac.sourceType=:sourceType")
		void deleteAccountReceivablesByBillIdAndStatus(@Param("sourceRef")String sourceRef, 
				@Param("sourceType")String sourceType,
				@Param("paymentStatus")String paymentStatus);
		
}