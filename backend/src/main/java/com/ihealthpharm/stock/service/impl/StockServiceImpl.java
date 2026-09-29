package com.ihealthpharm.stock.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.masters.dto.ItemsForStockAdjustDTO;
import com.ihealthpharm.masters.model.ItemsModel;
import com.ihealthpharm.masters.model.PharmacyModel;
import com.ihealthpharm.stock.dao.StockRepository;
import com.ihealthpharm.stock.dto.StockAdjustmentDTO;
import com.ihealthpharm.stock.dto.StockProfitDTO;
import com.ihealthpharm.stock.dto.StockRevenueDTO;
import com.ihealthpharm.stock.helper.StockHelper;
import com.ihealthpharm.stock.model.StockModel;
import com.ihealthpharm.stock.service.StockService;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class StockServiceImpl implements StockService {

	@Autowired
	StockRepository  stockRepository;

	@Autowired
	StockHelper stockHelper;

	@Override
	public StockModel saveStock(StockModel stockModel) {
		stockModel = stockRepository.save(stockModel);
		log.info("Stock data with ID: " + stockModel.getStockId() + " saved succesfully");
		return stockModel;
	}

	@Override
	public List<StockModel> saveStock(List<StockModel> stockModels) {
		for(int i=0;i<stockModels.size();i++)
		{
			stockModels.set(i,stockRepository.save(stockModels.get(i)));
		}
		return stockModels;
	}

	@Override
	public StockModel updateStock(StockModel stockModel) {
		StockModel model = getStockModel(stockModel.getStockId());
		if (!Objects.nonNull(model)) {
			throw new IHealthPharmException(stockHelper.getNotFoundStockMessage(), HttpStatus.NOT_FOUND);
		}
		model = stockRepository.save(stockModel);
		log.info("Stock data with ID : " + model.getStockId() + " updated succesfully");
		return model;
	}

	@Override
	public List<StockModel> updateStocks(List<StockModel> stockModels) {
		for (StockModel stockModel : stockModels) {
			StockModel model = getStockModel(stockModel.getStockId());
			if (!Objects.nonNull(model)) {
				throw new IHealthPharmException(stockHelper.getNotFoundStockMessage(), HttpStatus.NOT_FOUND);
			}
			model = stockRepository.save(stockModel);
			log.info("Stock data with Multiple IDs : " + model.getStockId() + " updated succesfully");
		}
		return stockModels;
	}

	@Override
	public List<StockModel> findAllStocks() {
		return stockRepository.findAllByOrderByCreationTimeStampDesc();
	}

	@Override
	public StockModel findStockById(Integer stockId) {
		StockModel stockModel = getStockModel(stockId);
		if (!Objects.nonNull(stockModel)) {
			throw new IHealthPharmException(stockHelper.getNotFoundStockMessage(), HttpStatus.NOT_FOUND);
		}
		log.info("Stock data with ID : "+ stockModel.getStockId()+" retrieved succesfully");
		return stockModel;
	}

	@Override
	public void deleteStockById(Integer stockId) {
		StockModel stockModel = getStockModel(stockId);
		if (!Objects.nonNull(stockModel)) {
			throw new IHealthPharmException(stockHelper.getNotFoundStockMessage(), HttpStatus.NOT_FOUND);
		}		
		stockRepository.delete(stockModel);
		log.info("Stock data with ID: "+ stockModel.getStockId()+" deleted succesfully");

	}

	@Override
	public void deleteStockByTds(Integer[] stockIds) {
		for (Integer stockId : stockIds) {
			StockModel stockModel = getStockModel(stockId);
			if (!Objects.nonNull(stockModel)) {
				throw new IHealthPharmException(stockHelper.getNotFoundStockMessage(), HttpStatus.NOT_FOUND);
			}
			stockRepository.delete(stockModel);
			log.info("Stock data with ID: "+ stockModel.getStockId()+" deleted succesfully");
		}

	}

	private StockModel getStockModel(Integer stockId) {
		StockModel stockModel = null;
		try {
			stockModel = stockRepository.findById(stockId).get();
			return stockModel;
		} catch (NoSuchElementException noSuchElementException) {
			throw new IHealthPharmException(stockHelper.getNotFoundStockMessage(), HttpStatus.NOT_FOUND);
		}

	}

	@Override
	public List<ItemsModel> findAllStockItems() {
		List<ItemsModel> listOfItems = stockRepository.findAllItem();
		log.info("All items in stock retrieved succesfully");
		return listOfItems;
	}

	@Override
	public List<String> getBatchNumbersByItemId(ItemsModel itemId) {

		return stockRepository.getBatchNumbersByItemId(itemId);
	}

	@Override
	public StockModel getStockByItemAndBatchNumber(ItemsModel itemId, String batchNo) {

		return stockRepository.findByItemAndBatchNo(itemId, batchNo);
	}

	@Override
	public List<StockModel> findByItem(ItemsModel itemId) {

		return stockRepository.findByItem(itemId);
	}

	@Override
	public List<StockModel> findByItemName(ItemsModel itemName) {

		return stockRepository.findByItemName(itemName);
	}

	@Override
	public List<StockModel> findByItemAndPharmacy(List<ItemsModel> itemList, PharmacyModel pharmacy) {
		List<StockModel> listOfStock = new ArrayList<>();
		List<StockModel> listOfStockRes = null;
		for(ItemsModel item:itemList)
		{
			listOfStockRes = stockRepository.findByItemAndPharmacy(item, pharmacy);
			if(Objects.nonNull(listOfStockRes))
			{
				listOfStock.addAll(listOfStockRes);
			}
		}
		return listOfStock;
	}

	@Override
	public StockModel getStockByItemIdandInvoiceId(Integer itemId, Integer invoiceId) {
		return null;//stockRepository.getStockByItemIdandInvoiceId(itemId, invoiceId);
	}

	@Override
	public StockModel findStocksByBillId(Integer itemId) {
		StockModel response=stockRepository.getStockDataBillId(itemId);
		return response;
	}

	@Override
	public List<StockModel> findByItemAndPharmacy(String searchTerm,String searchCode, Integer pharmacyId, Integer pageNumber, Integer pageSize) {
		Pageable limit = new PageRequest(pageNumber,pageSize);
		List<StockModel> res=null;
		if(searchCode.equalsIgnoreCase("Item Name"))
		{
			res = stockRepository.findStockByItemNameAndPharmacyId(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Item Code"))
		{
			res = stockRepository.findStockByItemCodeAndPharmacyId(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Description"))
		{
			res = stockRepository.findStockByItemDescriptionAndPharmacyId(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Batch Number"))
		{
			res = stockRepository.findStockByBatchNumberAndPharmacyId(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Generic Name"))
		{
			res = stockRepository.findStockByItemGenericNameAndPharmacyId(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("BarCode"))
		{
			res = stockRepository.findStockByQRBarCodeNumberAndPharmacyId(searchTerm,pharmacyId);
		}
		return res;
	}


	@Override
	public List<StockModel> findByItemAndPharmacyInEditStock(String searchTerm, String searchCode,
			Integer pharmacyId, Integer pageNumber, Integer pageSize) {
		Pageable limit = new PageRequest(pageNumber,pageSize);
		List<StockModel> res=null;
		if(searchCode.equalsIgnoreCase("Item Name"))
		{
			res = stockRepository.findStockByItemNameAndPharmacyIdInEditStock(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Item Code"))
		{
			res = stockRepository.findStockByItemCodeAndPharmacyIdInEditStock(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Description"))
		{
			res = stockRepository.findStockByItemDescriptionAndPharmacyIdInEditStock(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Batch Number"))
		{
			res = stockRepository.findStockByBatchNumberAndPharmacyIdInEditStock(searchTerm,pharmacyId,limit);
		}
		else if(searchCode.equalsIgnoreCase("Generic Name"))
		{
			res = stockRepository.findStockByItemGenericNameAndPharmacyIdInEditStock(searchTerm,pharmacyId,limit);

		}
		else if(searchCode.equalsIgnoreCase("BarCode"))
		{
			res = stockRepository.findStockByQRBarCodeNumberAndPharmacyIdInEditStock(searchTerm,pharmacyId);
		}

		return res;
	}




	@Override
	public Integer findByItemAndPharmacyCount(String searchTerm, String searchCode, Integer pharmacyId) {
		Integer res=0;
		if(searchCode.equalsIgnoreCase("Item Name"))
		{
			res = stockRepository.findStockByItemNameAndPharmacyId(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Item Code"))
		{
			res = stockRepository.findStockByItemCodeAndPharmacyId(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Description"))
		{
			res = stockRepository.findStockByItemDescriptionAndPharmacyId(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Batch Number"))
		{
			res = stockRepository.findStockByBatchNumberAndPharmacyId(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Generic Name"))
		{
			res = stockRepository.findStockByItemGenericNameAndPharmacyId(searchTerm,pharmacyId);
		}

	
		else if(searchCode.equalsIgnoreCase("BarCode"))
		{
			res = stockRepository.findStockCountByQRBarCodeNumberAndPharmacyId(searchTerm,pharmacyId);
		}



		return res;

	}


	@Override
	public Integer findByItemAndPharmacyCountInEditStock(String searchTerm, String searchCode, Integer pharmacyId) {
		Integer res=0;
		if(searchCode.equalsIgnoreCase("Item Name"))
		{
			res = stockRepository.findStockByItemNameAndPharmacyIdInEditStock(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Item Code"))
		{
			res = stockRepository.findStockByItemCodeAndPharmacyIdInEditStock(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Description"))
		{
			res = stockRepository.findStockByItemDescriptionAndPharmacyIdInEditStock(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Batch Number"))
		{
			res = stockRepository.findStockByBatchNumberAndPharmacyIdInEditStock(searchTerm,pharmacyId);
		}
		else if(searchCode.equalsIgnoreCase("Generic Name"))
		{
			res = stockRepository.findStockByItemGenericNameAndPharmacyIdInEditStock(searchTerm,pharmacyId);
		}
		
		else if(searchCode.equalsIgnoreCase("BarCode"))
		{
			res = stockRepository.findStockCountByQRBarCodeNumberAndPharmacyIdInEditStock(searchTerm,pharmacyId);
		}


		return res;

	}


	@Override
	public List<String> findSuppliersByStock(String searchTerm) {
		return stockRepository.findSupplierInStockPOL(searchTerm);
	}

	@Override
	public List<String> findAllSuppliersByStock() {
		return stockRepository.findAllSuppliersInStockPOL();
	}

	@Override
	public List<String> findManufacturerByStock(String searchTerm) {
		return stockRepository.findManufacturerInStockPOL(searchTerm);
	}

	@Override
	public List<String> findAllManufacturerByStock() {
		return stockRepository.findAllManufacturerInStockPOL();
	}

	@Override
	public List<String> findInvoiceDatesByStock(String searchTerm) {
		return stockRepository.findInvoiceDatesInStockPOL(searchTerm);
	}

	@Override
	public List<String> findAllInvoiceDatesByStock() {
		return stockRepository.findAllInvoiceDatesInStockPOL();
	}

	public List<StockModel> findAllByBatchNo(String searchTerm) {
		return stockRepository.findAllByBatchNoSearch(searchTerm);
	}
	//Supplier By MFR List
	@Override
	public List<String> findSupplierbynameInStockSBML(String searchTerm) {
		return stockRepository.findSupplierbynameInStockSBML(searchTerm);
	}

	@Override
	public List<String> findallSBML() {
		return stockRepository.findallSBML();
	}

	@Override
	public List findProfitService() {
		Pageable limit = new PageRequest(0,10);
		List<StockProfitDTO> res=stockRepository.profitPercentageRepo(limit);
		List finalObj = new ArrayList();
		for(StockProfitDTO obj:res) {
			List temp = new ArrayList();
			temp.add(obj.getName());
			temp.add(obj.getProfit());
			finalObj.add(temp);

		}
		return finalObj;
	}

	@Override
	public List findSuppliersRevenue() {
		Pageable limit = new PageRequest(0,5);
		List<StockRevenueDTO> res = stockRepository.suppliersRevenueRepo(limit);
		List finalObj = new ArrayList();
		for(StockRevenueDTO obj:res) {
			List temp = new ArrayList();
			temp.add(obj.getName());
			temp.add(obj.getRevenue());
			finalObj.add(temp);
		}
		return finalObj;
	}

	@Override
	public List<StockAdjustmentDTO> getAllBatchesOnItemCode(String searchTerm) {
		return stockRepository.findAllBatchesOnCode(searchTerm);
	}

	@Override
	public String getStockExpiryDate(Integer  itemId, String batch) {
		String res=stockRepository.getExpiryDate(itemId,batch);
		return res;
	}

	@Override
	public List<StockAdjustmentDTO> getAllBatchesOnItemName(String searchTerm) {
		return stockRepository.findAllBatchesBasedOnName(searchTerm);
	}

	@Override
	public List<StockAdjustmentDTO> getAllBatchesOnItemDesc(String searchTerm) {
		System.out.println(searchTerm);
		return stockRepository.findAllBatchesBasedOnItemDesc(searchTerm);
	}

	@Override
	public String getStocksExpiryDates(Integer itemId, String batch) {
		String res=stockRepository.getExpiryDates(itemId,batch);
		return res;
	}

	@Override
	public List<StockAdjustmentDTO> getAllBatchesOnItemGenericName(String searchTerm) {
		return stockRepository.findAllBatchesBasedOnItemGeneric(searchTerm);
	}

	@Override
	public String getStocksExpiryDatesByGeneric(Integer itemId, String batch) {
		return stockRepository.getExpiryDateBasedOnGeneric(itemId,batch);
	}

	@Override
	public String getStockExpiryBasedOnItemName(Integer  itemId, String batch) {
		return stockRepository.getExpiryDateByItemName( itemId,  batch);
	}

	@Override
	public List<StockModel> findStockByItemIdAndPharmacyId(Integer itemId, Integer pharmacyId) {
		return stockRepository.getStockByItemIdAndPharmacyId(itemId,pharmacyId);
	}

	@Override
	public Integer updateStock(Integer stockId,Integer previousQty, Integer quantity,Integer lastUpdateUser) {

		Date lastUpdateTimestamp = new Date();
		String entryType="Stock Take";
		return stockRepository.updateStockData(stockId,previousQty,quantity,lastUpdateUser,entryType,lastUpdateTimestamp);
	}

	@Override
	public Integer updateStockMrp(Integer stockId, Double mrp) {
		return stockRepository.updateStockMrp(stockId, mrp);
	}

	@Override
	public List<ItemsForStockAdjustDTO> getStockAdjustRecords(Integer stockId) {
		return stockRepository.getStockRecordById(stockId);
	}

	@Override
	public List<String> findInvoiceNosBySearchST(String searchTerm) {
		return stockRepository.findInvoiceNoInST(searchTerm);
	}

	@Override
	public List<String> findAllInvoiceNosByST() {
		return stockRepository.findAllInvoiceNoInST();
	}

	@Override
	public List<String> findItemNamesBySearchST(String searchTerm) {
		return stockRepository.findItemNmaesInST(searchTerm);
	}

	@Override
	public List<String> findAllItemNamesByST() {
		return stockRepository.findAllItemNmaesInST();
	}

	@Override
	public StockModel getLatestStock(String batchNo, Integer itemId, String invoiceNo) {
		return stockRepository.getLatestStock(batchNo,itemId,invoiceNo);
	}



		@Override
		public StockModel findByItemBatchExpiryAndPharmacy(String itemName, String batchNo, String expiryDt,
				Integer pharmacyId) {
			StockModel stock=stockRepository.findStockByItemNameBatchExpiryAndPharmacyId(itemName,batchNo,expiryDt,pharmacyId);
			return stock;
		}
		
		


}
