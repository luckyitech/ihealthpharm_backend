package com.ihealthpharm.reports.helper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import com.ihealthpharm.commons.JsonUtility;
import com.ihealthpharm.exception.IHealthPharmException;
import com.ihealthpharm.reports.dto.ReportsSearchCriteria;
import com.ihealthpharm.reports.model.ReportsMappingModel;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReportsCommonUtility {

	private static final DateTimeFormatter REPORT_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

	public String prepareSQL(ReportsMappingModel model) {
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append(" { ");
		stringBuffer.append(" CALL ");
		stringBuffer.append(model.getStoredProcedureName());
		stringBuffer.append(" } ");
		return stringBuffer.toString();
	}

	/**
	 * Builds the dynamic WHERE clause for a report from client-supplied filter
	 * values. This engine hands the clause to a stored procedure as text rather
	 * than binding it through JDBC placeholders, so every value below is
	 * validated/escaped before it is concatenated - column names and aliases
	 * come from the report's own server-side configuration (ReportsMappingModel),
	 * never from the client, but the filter values in dataMap are attacker
	 * controlled and must never be trusted verbatim.
	 */
	public String prepareWhereClause(ReportsMappingModel model, Map<String, Object> dataMap) {
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append(" WHERE 1 = 1 ");

		List<ReportsSearchCriteria> criteriaList = parseCriteria(model.getInputParameters());
		if (ObjectUtils.isEmpty(criteriaList)) {
			return stringBuffer.toString();
		}

		for (ReportsSearchCriteria criteria : criteriaList) {
			if (!dataMap.containsKey(criteria.getColumnName())) {
				continue;
			}

			Object value = dataMap.get(criteria.getColumnName());
			String column = criteria.getAlias() + criteria.getColumnName();
			stringBuffer.append(isOrCondition(criteria) ? " OR " : " AND ");

			//TODO need tune this logic
			if (StringUtils.equalsIgnoreCase("String", criteria.getType())) {
				appendStringCondition(stringBuffer, column, criteria.getOperator(), value);
			} else if (StringUtils.equalsIgnoreCase("Number", criteria.getType())) {
				appendNumberCondition(stringBuffer, column, criteria.getOperator(), value);
			} else if (StringUtils.equalsIgnoreCase("Date", criteria.getType())) {
				appendDateCondition(stringBuffer, column, criteria.getOperator(), value);
			}
		}

		return stringBuffer.toString();
	}

	@SuppressWarnings("unchecked")
	private List<ReportsSearchCriteria> parseCriteria(String inputParameters) {
		if (ObjectUtils.isEmpty(inputParameters)) {
			return null;
		}
		try {
			return JsonUtility.jsonToList(inputParameters, ReportsSearchCriteria.class);
		} catch (Exception e) {
			log.error(ExceptionUtils.getStackTrace(e));
			return null;
		}
	}

	private boolean isOrCondition(ReportsSearchCriteria criteria) {
		return "COUNTER_PARTY_ACCOUNT_TYPE".equals(criteria.getColumnName())
				|| "COUNTER_PARTY".equals(criteria.getColumnName());
	}

	private void appendStringCondition(StringBuffer sql, String column, String operator, Object value) {
		String safeValue = escapeSqlStringLiteral(value);
		if (StringUtils.equalsIgnoreCase("EQ", operator)) {
			sql.append(column).append(" = \"").append(safeValue).append("\" ");
		} else if (StringUtils.equalsIgnoreCase("LIKE", operator)) {
			sql.append(column).append(" LIKE \"").append(safeValue).append("%\" ");
		} else if (StringUtils.equalsIgnoreCase("NEQ", operator)) {
			sql.append(column).append(" != \"").append(safeValue).append("\" ");
		}
	}

	private void appendNumberCondition(StringBuffer sql, String column, String operator, Object value) {
		if (StringUtils.equalsIgnoreCase("LIKE", operator)) {
			// Historical behaviour: numeric "LIKE" is a string prefix match, so it
			// still needs quoting/escaping rather than the numeric validation below.
			sql.append(column).append(" LIKE \"").append(escapeSqlStringLiteral(value)).append("%\" ");
			return;
		}

		String safeValue = validateNumber(value);
		if (StringUtils.equalsIgnoreCase("EQ", operator)) {
			sql.append(column).append(" = ").append(safeValue).append(" ");
		} else if (StringUtils.equalsIgnoreCase("GE", operator)) {
			sql.append(column).append(" >= ").append(safeValue).append(" ");
		} else if (StringUtils.equalsIgnoreCase("LE", operator)) {
			sql.append(column).append(" <= ").append(safeValue).append(" ");
		}
	}

	// Date format should be YYYY-MM-DD
	private void appendDateCondition(StringBuffer sql, String column, String operator, Object value) {
		String safeValue = validateDate(value);
		String dateExpression = "DATE_FORMAT(" + column + ",'%Y-%m-%d')";

		if (StringUtils.equalsIgnoreCase("EQ", operator)) {
			sql.append(dateExpression).append(" = '").append(safeValue).append("' ");
		} else if (StringUtils.equalsIgnoreCase("GE", operator)) {
			sql.append(dateExpression).append(" >= '").append(safeValue).append("' ");
		} else if (StringUtils.equalsIgnoreCase("LE", operator)) {
			sql.append(dateExpression).append(" <= '").append(safeValue).append("' ");
		}
	}

	/**
	 * Escapes a value for safe inclusion inside a quoted SQL string literal.
	 * Doubles up backslashes first (MySQL's default escape character) and then
	 * escapes both quote styles so a value can never terminate the literal early
	 * regardless of which delimiter the caller wraps it in.
	 */
	private String escapeSqlStringLiteral(Object value) {
		String text = value == null ? "" : value.toString();
		return text.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
	}

	private String validateNumber(Object value) {
		String text = value == null ? null : value.toString().trim();
		try {
			return new BigDecimal(text).toPlainString();
		} catch (NumberFormatException | NullPointerException e) {
			throw new IHealthPharmException("Invalid numeric report filter value: " + text, HttpStatus.BAD_REQUEST);
		}
	}

	private String validateDate(Object value) {
		String text = value == null ? null : value.toString().trim();
		try {
			return LocalDate.parse(text, REPORT_DATE_FORMAT).format(REPORT_DATE_FORMAT);
		} catch (DateTimeParseException | NullPointerException e) {
			throw new IHealthPharmException(
					"Invalid date report filter value, expected YYYY-MM-DD: " + text, HttpStatus.BAD_REQUEST);
		}
	}

}
