package com.ihealthpharm.stock.dao;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ihealthpharm.masters.dto.ItemsForStockAdjustDTO;
import com.ihealthpharm.masters.model.ItemsModel;
import com.ihealthpharm.masters.model.PharmacyModel;
import com.ihealthpharm.stock.dto.StockAdjustmentDTO;
import com.ihealthpharm.stock.dto.StockProfitDTO;
import com.ihealthpharm.stock.dto.StockRevenueDTO;
import com.ihealthpharm.stock.model.StockModel;

@Repository
public interface StockRepository extends JpaRepository<StockModel, Integer> {

	List<StockModel> findAllByOrderByCreationTimeStampDesc();

	@Query("select s.item from stock s")
	List<ItemsModel> findAllItem();

	@Query("select s.batchNo from stock s where item = :itemId")
	List<String> getBatchNumbersByItemId(@Param("itemId") ItemsModel itemId);

	StockModel findByItemAndBatchNo(ItemsModel itemId, String batchNo);

	@Query("select i from stock i where i.item like %:searchTerm%")
	List<StockModel> findByItemName(@Param("searchTerm") ItemsModel searchTerm);

	List<StockModel> findByItem(ItemsModel itemId);

	List<StockModel> findByItemAndPharmacy(ItemsModel itemId, PharmacyModel pharmacy);

	@Query("select s from stock s where s.item.itemId=:itemId")
	StockModel getStockDataBillId(Integer itemId);

	// find stock by item name
	@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemName like :searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	List<StockModel> findStockByItemNameAndPharmacyId(@Param("searchTerm") String itemName,
			@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

	// find stock by barcode
	@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.barcode like %:searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	List<StockModel> findStockByQRBarCodeNumberAndPharmacyId(@Param("searchTerm")String searchTerm, @Param("pharmacyId")Integer pharmacyId);
	
	@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.barcode like %:searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	Integer findStockCountByQRBarCodeNumberAndPharmacyId(@Param("searchTerm")String searchTerm, @Param("pharmacyId")Integer pharmacyId);
	
	// find stock by item code
	@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemCode like :searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	List<StockModel> findStockByItemCodeAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

	// find stock by item Description
	@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemDescription like %:searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	List<StockModel> findStockByItemDescriptionAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

	// find stock by stock batch number
	@Query("select s from stock s  where s.batchNo like :searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
			+ "and s.quantity>0 order by s.expiryDt asc")
	List<StockModel> findStockByBatchNumberAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

	// find stock by item generic name
	@Query("select s from stock s inner join items i on s.item.itemId=i.itemId inner join "
			+ "items_generic_names ig on ig.itemGenericNameId=i.itemGenericName.itemGenericNameId "
			+ "where ig.genericName like %:searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
					+ "and s.quantity>0 order by i.itemName,s.expiryDt asc")
	List<StockModel> findStockByItemGenericNameAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

	// find stock by item name
	@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemName like :searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	Integer findStockByItemNameAndPharmacyId(@Param("searchTerm") String itemName,
			@Param("pharmacyId") Integer pharmacyId);

	// find stock by item code
	@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemCode like :searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	Integer findStockByItemCodeAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId);

	// find stock by item Description
	@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemDescription like %:searchTerm% "
			+ "and s.pharmacy.pharmacyId=:pharmacyId and s.quantity>0 order by i.itemName,s.expiryDt asc")
	Integer findStockByItemDescriptionAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId);

	// find stock by stock batch number
	@Query("select count(s) from stock s  where s.batchNo like :searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
			+ "and s.quantity>0 order by s.expiryDt asc")
	Integer findStockByBatchNumberAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId);

	// find stock by item Description
	@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId inner join "
			+ "items_generic_names ig on ig.itemGenericNameId=i.itemGenericName.itemGenericNameId "
			+ "where ig.genericName like %:searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
					+ "and s.quantity>0 order by i.itemName,s.expiryDt asc")
	Integer findStockByItemGenericNameAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId);

	//find supplier by search
	@Query("select distinct sp.name from stock st,invoice_items init,invoice inv,items i,manufacturer m,supplier sp " 
			+ "where init.itemsModel.itemId=st.item.itemId and inv.invoiceId=init.invoice.invoiceId and st.item.itemId=i.itemId and i.manufacturer.manufacturerId=m.manufacturerId "
			+ "and inv.supplierModel.supplierId=sp.supplierId and sp.name like :searchTerm% ")
	List<String> findSupplierInStockPOL(@Param("searchTerm") String searchTerm);

	//find all suppliers by stock	
	@Query("select distinct sp.name from stock st,invoice_items init,invoice inv,items i,manufacturer m,supplier sp "
			+ "where init.itemsModel.itemId=st.item.itemId and inv.invoiceId=init.invoice.invoiceId and st.item.itemId=i.itemId and i.manufacturer.manufacturerId=m.manufacturerId "
			+"and inv.supplierModel.supplierId=sp.supplierId order by sp.name ")
	List<String> findAllSuppliersInStockPOL();

	//find manufacturer by search	
	@Query("select distinct m.name from stock st,invoice_items init,invoice inv,items i,manufacturer m,supplier sp "  
			+"where init.itemsModel.itemId=st.item.itemId and inv.invoiceId=init.invoice.invoiceId and st.item.itemId=i.itemId and i.manufacturer.manufacturerId=m.manufacturerId "  
			+"and inv.supplierModel.supplierId=sp.supplierId and m.name like :searchTerm%  ")
	List<String> findManufacturerInStockPOL(@Param("searchTerm") String searchTerm);

	//find all manufacturers by stock	
	@Query("select distinct m.name from stock st,invoice_items init,invoice inv,items i,manufacturer m,supplier sp "  
			+"where init.itemsModel.itemId=st.item.itemId and inv.invoiceId=init.invoice.invoiceId and st.item.itemId=i.itemId and i.manufacturer.manufacturerId=m.manufacturerId "  
			+"and inv.supplierModel.supplierId=sp.supplierId order by m.name  ")
	List<String> findAllManufacturerInStockPOL();

	//find invoice dates by search
	@Query("select distinct inv.invoiceDt from stock st,invoice_items init,invoice inv,items i,manufacturer m,supplier sp "  
			+"where init.itemsModel.itemId=st.item.itemId and inv.invoiceId=init.invoice.invoiceId and st.item.itemId=i.itemId and i.manufacturer.manufacturerId=m.manufacturerId "  
			+"and inv.supplierModel.supplierId=sp.supplierId and inv.invoiceDt>=:searchTerm  ")
	List<String> findInvoiceDatesInStockPOL(@Param("searchTerm") String searchTerm);
	//find all invoice dates by stock

	@Query("select distinct inv.invoiceDt from stock st,invoice_items init,invoice inv,items i,manufacturer m,supplier sp "  
			+"where init.itemsModel.itemId=st.item.itemId and inv.invoiceId=init.invoice.invoiceId and st.item.itemId=i.itemId and i.manufacturer.manufacturerId=m.manufacturerId "  
			+"and inv.supplierModel.supplierId=sp.supplierId order by inv.invoiceDt  ")
	List<String> findAllInvoiceDatesInStockPOL();

	@Query("select st from stock st where st.batchNo like %:searchTerm% order by st.creationTimeStamp desc")
	List<StockModel> findAllByBatchNoSearch(@Param("searchTerm") String searchTerm);

	//Supplier By Mfr List
	@Query("select distinct sp.name from stock st,supplier sp,items i,manufacturer m " 
			+ "where st.item.itemId=i.itemId "
			+ "and i.manufacturer.manufacturerId=m.manufacturerId "
			+ "and st.supplier.supplierId=sp.supplierId and sp.name like :searchTerm% ")  
	List<String> findSupplierbynameInStockSBML(@Param("searchTerm") String searchTerm);

	@Query("select distinct sp.name from stock st,supplier sp,items i,manufacturer m " 
			+ "where st.item.itemId=i.itemId "
			+ "and i.manufacturer.manufacturerId=m.manufacturerId "
			+ "and st.supplier.supplierId=sp.supplierId order by sp.name")
	List<String> findallSBML();

	// stock adjustment searches to get date and batch numbers
	@Query("select new com.ihealthpharm.stock.dto.StockAdjustmentDTO(b.batchNo)  from stock b  inner join items i on b.item.itemId=i.itemId where i.itemCode=:searchTerm")
	List<StockAdjustmentDTO> findAllBatchesOnCode(@Param("searchTerm") String searchTerm);

	@Query("select new com.ihealthpharm.stock.dto.StockAdjustmentDTO(b.batchNo)  from stock b  inner join items i on b.item.itemId=i.itemId where i.itemName =:searchTerm")
	List<StockAdjustmentDTO> findAllBatchesBasedOnName(@Param("searchTerm") String searchTerm);

	@Query("select new com.ihealthpharm.stock.dto.StockAdjustmentDTO(b.batchNo)  from stock b  inner join items i on b.item.itemId=i.itemId where i.itemDescription =:searchTerm")
	List<StockAdjustmentDTO> findAllBatchesBasedOnItemDesc(@Param("searchTerm") String searchTerm);

	@Query("select new com.ihealthpharm.stock.dto.StockAdjustmentDTO(b.batchNo) from stock b inner join items i on b.item.itemId=i.itemId where i.itemGenericName.genericName =:searchTerm ")
	List<StockAdjustmentDTO> findAllBatchesBasedOnItemGeneric(@Param("searchTerm") String searchTerm);

	@Query("select b.expiryDt from stock b  inner join items i on b.item.itemId=i.itemId inner join items_generic_names ig on i.itemId=ig.itemGenericNameId where i.itemId =:itemId "+ 
			"and b.batchNo=:batch")
	String getExpiryDateBasedOnGeneric(@Param("itemId") Integer itemId,@Param("batch") String batch);

	@Query("select b.expiryDt  from stock b  inner join items i on b.item.itemId=i.itemId where i.itemId=:itemId and b.batchNo=:batch")
	String getExpiryDateByItemName(@Param("itemId") Integer itemId,@Param("batch") String batch);

	@Query("select b.expiryDt  from stock b  inner join items i on b.item.itemId=i.itemId where i.itemId=:itemId and b.batchNo=:batch ")
	String getExpiryDate(@Param("itemId") Integer itemId,@Param("batch") String batch);

	@Query("select b.expiryDt  from stock b  inner join items i on b.item.itemId=i.itemId where  i.itemId=:itemId and b.batchNo=:batch ")
	String getExpiryDates(@Param("itemId") Integer itemId,@Param("batch") String batch);

	@Query("select new com.ihealthpharm.stock.dto.StockProfitDTO(sup.name, ((sum(s.unitSaleRate) - sum(s.unitPurchaseRate))/ sum(s.unitSaleRate)) * 100 as profit) from stock s inner join supplier sup on s.supplier = sup.supplierId where s.unitSaleRate > s.unitPurchaseRate group by sup.name order by profit desc") 
	List<StockProfitDTO> profitPercentageRepo(Pageable pageable);

	@Query("select new com.ihealthpharm.stock.dto.StockRevenueDTO(sup.name, sum((s.quantity * s.unitSaleRate)/1000) as revenue) from supplier sup join stock s on s.supplier = sup.supplierId group by sup.name order by revenue desc")
	List<StockRevenueDTO> suppliersRevenueRepo(Pageable pageable);

	@Query("select s from stock s where s.item.itemId =:itemId and s.pharmacy.pharmacyId =:pharmacyId order by s.stockDt desc")
	List<StockModel> getStockByItemIdAndPharmacyId(@Param("itemId") Integer itemId,@Param("pharmacyId") Integer pharmacyId);

	List<StockModel> findByItem(Integer itemId);

	@Transactional
	@Modifying
	@Query("update stock s set s.quantity=:quantity,s.lastUpdateUser=:lastUpdateUser,ENTRY_TYPE=:entryType,s.lastUpdateTimestamp=:lastUpdateTimestamp where s.quantity=:previousQty and  s.stockId=:stockId")
	Integer updateStockData(@Param("stockId") Integer stockId,@Param("previousQty")Integer previousQty, @Param("quantity") Integer quantity,@Param("lastUpdateUser")Integer lastUpdateUser,@Param("entryType")String entryType,@Param("lastUpdateTimestamp")Date lastUpdateTimestamp);

	@Transactional
	@Modifying
	@Query("update stock s set s.mrp=:mrp where s.stockId=:stockId")
	Integer updateStockMrp(@Param("stockId") Integer stockId, @Param("mrp") Double mrp);

	@Query("select new  com.ihealthpharm.masters.dto.ItemsForStockAdjustDTO(s.stockId,i.itemName,s.item.itemId,s.invoiceNo,s.remarks,s.rack,s.batchNo,s.expiryDt,s.quantity,s.quantity,s.shelf) from stock s inner join items i on s.item=i.itemId where s.stockId=:stockId order by i.itemName,s.batchNo asc")
	List<ItemsForStockAdjustDTO> getStockRecordById(@Param("stockId") Integer stockId);


	//Stock Take report

	@Query("select distinct st.invoiceNo from stock st,stock_adjustment sa where sa.stock=st.stockId and st.invoiceNo like :searchTerm%")
	List<String> findInvoiceNoInST(@Param("searchTerm") String searchTerm);

	@Query("select distinct st.invoiceNo from stock st,stock_adjustment sa where sa.stock=st.stockId order by st.invoiceNo")
	List<String> findAllInvoiceNoInST();

	@Query("select distinct i.itemName from stock st,stock_adjustment sa,items i where sa.stock=st.stockId and st.item.itemId=i.itemId and i.itemName like :searchTerm%")
	List<String> findItemNmaesInST(@Param("searchTerm") String searchTerm);

	@Query("select distinct i.itemName from stock st,stock_adjustment sa,items i where sa.stock=st.stockId and st.item.itemId=i.itemId order by i.itemName")
	List<String> findAllItemNmaesInST();

	@Query("select s from stock s where s.batchNo=:batchNo and s.item.itemId=:itemId and s.invoiceNo=:invoiceNo")
	StockModel getLatestStock(@Param("batchNo")String batchNo,@Param("itemId") Integer itemId,@Param("invoiceNo") String invoiceNo);


	@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.barcode=:searchTerm "
			+ "and s.pharmacy.pharmacyId=:pharmacyId")
	List<StockModel> findStockByBarCodeAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

	@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.barcode=:searchTerm "
			+ "and s.pharmacy.pharmacyId=:pharmacyId")
	Integer findStockByBarCodeAndPharmacyId(@Param("searchTerm") String searchTerm,
			@Param("pharmacyId") Integer pharmacyId);

	@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemName=:itemName and s.batchNo=:batchNo "
			+ "and s.expiryDt=:expiryDt "
			+ "and s.pharmacy.pharmacyId=:pharmacyId")
	StockModel findStockByItemNameBatchExpiryAndPharmacyId(@Param("itemName")String itemName, @Param("batchNo")String batchNo, @Param("expiryDt")String expiryDt,
			@Param("pharmacyId")Integer pharmacyId);

	
	
	// find stock by item name In Edit Stock
		@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemName like :searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		Integer findStockByItemNameAndPharmacyIdInEditStock(@Param("searchTerm") String itemName,
				@Param("pharmacyId") Integer pharmacyId);

		// find stock by item code In EditStock
		@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemCode like :searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		Integer findStockByItemCodeAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId);

		// find stock by item Description In EditStock
		@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemDescription like %:searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		Integer findStockByItemDescriptionAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId);

		// find stock by stock batch number In EditStock
		@Query("select count(s) from stock s  where s.batchNo like :searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
				+ "order by s.expiryDt asc")
		Integer findStockByBatchNumberAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId);

		// find stock by item generic name In EditStock
		@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId inner join "
				+ "items_generic_names ig on ig.itemGenericNameId=i.itemGenericName.itemGenericNameId "
				+ "where ig.genericName like %:searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
						+ "order by i.itemName,s.expiryDt asc")
		Integer findStockByItemGenericNameAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId);
	
		
		
		// find stock by item name In EditStock
		@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemName like :searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		List<StockModel> findStockByItemNameAndPharmacyIdInEditStock(@Param("searchTerm") String itemName,
				@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

		
		// find stock by item code In EditStock
		@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemCode like :searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		List<StockModel> findStockByItemCodeAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

		// find stock by item Description In EditStock
		@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.item.itemDescription like %:searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		List<StockModel> findStockByItemDescriptionAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

		// find stock by stock batch number In EditStock
		@Query("select s from stock s  where s.batchNo like :searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
				+ "order by s.expiryDt asc")
		List<StockModel> findStockByBatchNumberAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

		// find stock by item generic name In EditStock
		@Query("select s from stock s inner join items i on s.item.itemId=i.itemId inner join "
				+ "items_generic_names ig on ig.itemGenericNameId=i.itemGenericName.itemGenericNameId "
				+ "where ig.genericName like %:searchTerm% " + "and s.pharmacy.pharmacyId=:pharmacyId "
						+ "order by i.itemName,s.expiryDt asc")
		List<StockModel> findStockByItemGenericNameAndPharmacyIdInEditStock(@Param("searchTerm") String searchTerm,
				@Param("pharmacyId") Integer pharmacyId, Pageable pageable);

		@Query("select count(s) from stock s inner join items i on s.item.itemId=i.itemId where s.barcode like %:searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		Integer findStockCountByQRBarCodeNumberAndPharmacyIdInEditStock(@Param("searchTerm")String searchTerm, @Param("pharmacyId")Integer pharmacyId);

		@Query("select s from stock s inner join items i on s.item.itemId=i.itemId where s.barcode like %:searchTerm% "
				+ "and s.pharmacy.pharmacyId=:pharmacyId order by i.itemName,s.expiryDt asc")
		List<StockModel> findStockByQRBarCodeNumberAndPharmacyIdInEditStock(@Param("searchTerm")String searchTerm, @Param("pharmacyId")Integer pharmacyId);
		
	
}
