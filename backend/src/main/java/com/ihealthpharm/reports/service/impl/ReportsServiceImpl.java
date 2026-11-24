package com.ihealthpharm.reports.service.impl;

import java.io.File;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.ihealthpharm.commons.JsonUtility;
import com.ihealthpharm.commons.TimeDurationUtility;
import com.ihealthpharm.config.SpringContextUtility;
import com.ihealthpharm.reports.dao.ReportsMappingRepository;
import com.ihealthpharm.reports.helper.ReportsCommonUtility;
import com.ihealthpharm.reports.helper.ReportsExcelUtility;
import com.ihealthpharm.reports.helper.ReportsPDFUtility;
import com.ihealthpharm.reports.model.ReportsMappingModel;
import com.ihealthpharm.reports.service.ExcelReportGenerator;
import com.ihealthpharm.reports.service.ReportGenerator;
import com.ihealthpharm.reports.service.ReportsService;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class ReportsServiceImpl implements ReportsService {

	@Autowired
	private ReportsMappingRepository reportsMappingRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private ReportsPDFUtility reportsPDFUtility;

	@Autowired
	private ReportsExcelUtility reportsExcelUtility;

	@Autowired
	private ReportsCommonUtility reportsCommonUtility;
	
	@SuppressWarnings("unchecked")
	@Override
	public File generateReport(HttpServletResponse response, String inputJson,String reportType) throws Exception {
		File responseFile = null;
		Date start = new Date();

		if(ObjectUtils.isEmpty(inputJson)) 
			throw new Exception("Input Parameters can't be blank ");
		
		Map<String,Object> dataMap= (Map<String, Object>) JsonUtility.jsonToMap(inputJson);
		
		if(!dataMap.containsKey("ReportCode"))
			throw new Exception("Report Code can't be blank ");	

		
		ReportsMappingModel model = reportsMappingRepository.findByReportCode(String.valueOf(dataMap.get("ReportCode")));
		
		// Download options, not filters: prepareWhereClause only reads the
		// configured input parameters, so these never reach the query.
		//   AS_TABLE      print the rows as a plain table, even when the report
		//                 has its own generator (a till-roll receipt, say), so a
		//                 list of rows comes out as one sheet or one A4 table.
		//   ONLY_ROWS_WITH {"column": [values]} keep only the rows whose column
		//                 holds one of the values - the rows ticked in the grid.
		boolean asTable = Boolean.parseBoolean(String.valueOf(dataMap.get("AS_TABLE")));
		Object onlyRowsWith = dataMap.get("ONLY_ROWS_WITH");

		List<Map<String,Object>> responseList =getReportData(model,dataMap);
		if (onlyRowsWith instanceof Map && responseList != null) {
			for (Map.Entry<String, Object> keep : ((Map<String, Object>) onlyRowsWith).entrySet()) {
				if (!(keep.getValue() instanceof Collection))
					continue;
				Set<String> values = new HashSet<>();
				for (Object v : (Collection<Object>) keep.getValue())
					values.add(String.valueOf(v));
				responseList.removeIf(row -> !values.contains(String.valueOf(row.get(keep.getKey()))));
			}
		}

		String extension =".xls";
		if(StringUtils.equalsIgnoreCase("PDF", reportType))
			extension =".pdf";
		
		// A file of its own per request. This used to be tmpdir + report name,
		// shared by every download of the same report: two at once (several
		// receipts, or two users) wrote over and deleted each other's file and
		// sent back corrupt or empty spreadsheets. The browser names the saved
		// file itself, so the random suffix is never seen.
		responseFile = File.createTempFile(model.getReportName().replaceAll("[^A-Za-z0-9]+", "_") + "_", extension);
		
		
		if (StringUtils.equalsIgnoreCase("PDF", reportType)) {
			
			if (asTable || StringUtils.isBlank(model.getCustomReportGeneratorClazz()))
				reportsPDFUtility.generateReport(responseList, model, responseFile,inputJson);
			else {
				Class beanClass = Class.forName(model.getCustomReportGeneratorClazz());
				ReportGenerator reportGenerator = (ReportGenerator) SpringContextUtility.getBean(beanClass);
				reportGenerator.generateReport(responseList, model, responseFile,inputJson);
			}				
		
		}else {
			
			if (asTable || StringUtils.isBlank(model.getCustomExcelReportGeneratorClazz()))
				reportsExcelUtility.generateReport(responseList, model, responseFile,inputJson);
			else {
				Class beanClass = Class.forName(model.getCustomExcelReportGeneratorClazz());
				ExcelReportGenerator reportGenerator = (ExcelReportGenerator) SpringContextUtility.getBean(beanClass);
				reportGenerator.generateReport(responseList, model, responseFile,inputJson);
			}			
			
			
		}

		log.info("Service Duration [{}]", TimeDurationUtility.duration(start));
		return responseFile;

	}

	@Override
	public List<Map<String, Object>> getReportData(String inputJson) throws Exception {

		if(ObjectUtils.isEmpty(inputJson)) 
			throw new Exception("Input Parameters can't be blank ");
		
		Map<String,Object> dataMap= (Map<String, Object>) JsonUtility.jsonToMap(inputJson);
		
		if(!dataMap.containsKey("ReportCode"))
			throw new Exception("Report Code can't be blank ");
		

		ReportsMappingModel model = reportsMappingRepository.findByReportCode(String.valueOf(dataMap.get("ReportCode")));

		List<Map<String,Object>> responseList =getReportData(model,dataMap);
		
		return responseList;
		
		
	}
	
	private List<Map<String, Object>> getReportData(ReportsMappingModel model, Map<String, Object> dataMap) throws Exception {
						
		if(ObjectUtils.isEmpty(model))
			throw new Exception("Reports Mapping Not found for the given Report code "+ dataMap.get("ReportCode"));
		
		
		String SQL = reportsCommonUtility.prepareSQL(model);
		String Whereclause = reportsCommonUtility.prepareWhereClause(model,dataMap);
		
		log.info("SQL [{}]",SQL);
		log.info("WhereClause[{}]",Whereclause);
		
		List<Map<String,Object>> responseList = jdbcTemplate.queryForList(SQL,Whereclause);	
		
		return responseList;
	}

	@Override
	public List<ReportsMappingModel> getReportsDetails() {
		return reportsMappingRepository.findAll();
	}

	@Override
	public ReportsMappingModel getReportsDetailsByReportCode(String reportCode) {
		
		return reportsMappingRepository.findByReportCode(reportCode);
	}

}
