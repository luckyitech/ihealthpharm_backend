package com.ihealthpharm.stock.service;

import java.util.List;

import com.ihealthpharm.masters.dto.ItemsForStockAdjustDTO;
import com.ihealthpharm.masters.model.ItemsModel;
import com.ihealthpharm.masters.model.PharmacyModel;
import com.ihealthpharm.stock.dto.StockAdjustmentDTO;
import com.ihealthpharm.stock.dto.StockProfitDTO;
import com.ihealthpharm.stock.dto.StockRevenueDTO;
import com.ihealthpharm.stock.model.StockModel;

public interface StockService {

	StockModel saveStock(StockModel stockModel);

	List<StockModel> saveStock(List<StockModel> stockModel);

	StockModel updateStock(StockModel stockModel);

	List<StockModel> updateStocks(List<StockModel> stockModels);

	List<StockModel> findAllStocks();

	StockModel findStockById(Integer stockId);

	void deleteStockById(Integer stockId);

	void deleteStockByTds(Integer[] stockIds);

	List<ItemsModel> findAllStockItems();

	List<String> getBatchNumbersByItemId(ItemsModel itemId);

	StockModel getStockByItemAndBatchNumber(ItemsModel itemId, String batchNo);

	List<StockModel> findByItem(ItemsModel itemId);

	List<StockModel> findByItemAndPharmacy(List<ItemsModel> itemId, PharmacyModel pharmacy);

	List<StockModel> findByItemAndPharmacy(String searchTerm,String searchCode, Integer pharmacyid, Integer pageNumber, Integer pageSize);

	Integer findByItemAndPharmacyCount(String searchTerm,String searchCode, Integer pharmacyid);

	List<StockModel> findByItemName(ItemsModel itemName);

	StockModel findStocksByBillId(Integer itemId);

	StockModel getStockByItemIdandInvoiceId(Integer itemId, Integer invoiceId);

	List<String> findSuppliersByStock(String searchTerm);

	List<String> findAllSuppliersByStock();

	List<String> findManufacturerByStock(String searchTerm);

	List<String> findAllManufacturerByStock();

	List<String> findInvoiceDatesByStock(String searchTerm);

	List<String> findAllInvoiceDatesByStock();

	List<StockModel> findAllByBatchNo(String searchTerm);

	List<String> findSupplierbynameInStockSBML(String searchTerm);

	List<String> findallSBML();

	List<StockAdjustmentDTO> getAllBatchesOnItemCode(String searchTerm);

	String getStockExpiryDate(Integer itemId, String batch);

	
	List<StockAdjustmentDTO> getAllBatchesOnItemName(String searchTerm);

	List<StockAdjustmentDTO> getAllBatchesOnItemDesc(String searchTerm);

	List<StockAdjustmentDTO> getAllBatchesOnItemGenericName(String searchTerm);
	
	String getStocksExpiryDates(Integer itemId, String batch);

	String getStocksExpiryDatesByGeneric(Integer itemId, String batch);

	String getStockExpiryBasedOnItemName(Integer itemId, String batch);

	List<StockProfitDTO> findProfitService();
	
	List<StockRevenueDTO> findSuppliersRevenue();

	List<StockModel> findStockByItemIdAndPharmacyId(Integer itemId, Integer pharmacyId);

	Integer updateStock(Integer stockId,Integer previousQty,Integer quantity,Integer lastUpdateUser);

	List<ItemsForStockAdjustDTO> getStockAdjustRecords(Integer stockId);
	
	List<String> findInvoiceNosBySearchST(String searchTerm);
	
	List<String> findAllInvoiceNosByST();
	
	List<String> findItemNamesBySearchST(String searchTerm);
	
	List<String> findAllItemNamesByST();

	StockModel getLatestStock(String batchNo, Integer itemId, String invoiceNo);


	StockModel findByItemBatchExpiryAndPharmacy(String itemName, String batchNo, String expiryDt, Integer pharmacyId);

	Integer findByItemAndPharmacyCountInEditStock(String searchTerm, String searchCode, Integer pharmacyId);

	List<StockModel> findByItemAndPharmacyInEditStock(String searchTerm, String searchCode, Integer pharmacyId,
			Integer pageNumber, Integer pageSize);

	Integer updateStockMrp(Integer stockId, Double mrp);


}
