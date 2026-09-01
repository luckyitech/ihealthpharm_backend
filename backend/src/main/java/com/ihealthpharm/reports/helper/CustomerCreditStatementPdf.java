package com.ihealthpharm.reports.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import com.ihealthpharm.commons.JsonUtility;
import com.ihealthpharm.reports.dto.HeaderFooterContentDetailsDto;
import com.ihealthpharm.reports.dto.HeaderFooterContentDto;
import com.ihealthpharm.reports.model.ReportsMappingModel;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.RectangleReadOnly;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Utilities;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * What a customer still owes, bill by bill, with what they bought and how long
 * they have owed it.
 *
 * Printed on the same 80mm roll as the receipt so it can be handed across the
 * counter, and carrying the same letterhead so a customer recognises where it
 * came from.
 *
 * The rows arrive one per item, with each bill's figures repeated against every
 * one of its items, so they are grouped back under their bill here. A flat
 * table restating the date, total and balance beside every product would not be
 * readable on a receipt printer.
 *
 * Ordered oldest debt first, which is the order the conversation with the
 * customer goes in. The ageing summary at the end is the part worth reading:
 * most of what this pharmacy is owed has been owed for over a year.
 */
@Component
@Slf4j
public class CustomerCreditStatementPdf extends ReportsPDFUtility {

	private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

	private static final Font BOLD = new Font(FontFamily.HELVETICA, 7, Font.BOLD);
	private static final Font PLAIN = new Font(FontFamily.HELVETICA, 6, Font.NORMAL);
	private static final Font HEADING = new Font(FontFamily.HELVETICA, 7, Font.BOLD);

	@Override
	public Document generateReport(List<Map<String, Object>> responseList, ReportsMappingModel model,
			File responseFile, String inputJson) {

		Document document = new Document(new RectangleReadOnly(Utilities.millimetersToPoints(80), 1400));

		try {
			PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(responseFile));
			// A till roll, not a letterhead - see AccountReceivablesReceiptPdf,
			// where wide margins had been pushing the last column off the paper.
			document.setMargins(8, 8, 0, 0);
			document.setMarginMirroringTopBottom(true);
			document.open();

			addHeader(writer, document, model);
			addWhoFor(document, responseList);
			addBills(document, responseList);
			addSummary(document, responseList);
			addFooter(writer, model, document);

		} catch (Exception e) {
			log.error(ExceptionUtils.getMessage(e));
			try {
				addMessage(document, ExceptionUtils.getMessage(e));
			} catch (DocumentException ignored) {
				log.error("could not write the failure onto the statement");
			}
		} finally {
			if (document.isOpen()) {
				document.close();
			}
		}
		return document;
	}

	/** Who the statement is for, and when it was taken. */
	private void addWhoFor(Document document, List<Map<String, Object>> rows) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new int[] { 2, 5 });
		table.setSpacingAfter(6);

		addPair(table, "As at", new java.text.SimpleDateFormat("dd-MM-yyyy").format(new java.util.Date()));
		addPair(table, "Customer", text(first(rows, "CUSTOMER_NAME")));

		String account = text(first(rows, "MASTER_ACC_NO"));
		addPair(table, "Account", account.isEmpty() ? "No credit account" : account);

		document.add(table);
	}

	private void addPair(PdfPTable table, String label, String value) {
		PdfPCell cell = new PdfPCell(new Phrase(label, BOLD));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(1f);
		table.addCell(cell);

		cell = new PdfPCell(new Phrase(value, BOLD));
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setPadding(1f);
		table.addCell(cell);
	}

	/**
	 * Each bill, then the items that made it up.
	 *
	 * The rows repeat a bill's figures against every item, so a bill is started
	 * whenever the code changes rather than once per row.
	 */
	private void addBills(Document document, List<Map<String, Object>> rows) throws DocumentException {
		if (ObjectUtils.isEmpty(rows)) {
			PdfPTable empty = new PdfPTable(1);
			empty.setWidthPercentage(100);
			PdfPCell cell = new PdfPCell(new Phrase("Nothing outstanding on this account.", BOLD));
			cell.setBorder(Rectangle.NO_BORDER);
			empty.addCell(cell);
			document.add(empty);
			return;
		}

		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		// The bill line carries a fourteen character code and a date, so the
		// first column has to hold twenty three characters before it wraps.
		table.setWidths(new int[] { 32, 7, 10, 11 });
		table.setSpacingAfter(6);

		headerRow(table, "BILL / ITEM", "DAYS", "PAID", "OWING");

		String currentBill = null;
		for (Map<String, Object> row : rows) {
			String billCode = text(row.get("BILL_CODE"));

			if (!billCode.equals(currentBill)) {
				currentBill = billCode;
				billRow(table, row, billCode);
			}

			String item = text(row.get("ITEM_NM"));
			if (!item.isEmpty()) {
				itemRow(table, item, text(row.get("SALE_QTY")));
			}
		}
		document.add(table);
	}

	private void headerRow(PdfPTable table, String... labels) {
		for (String label : labels) {
			PdfPCell cell = new PdfPCell(new Phrase(label, HEADING));
			cell.setBorder(Rectangle.BOTTOM);
			cell.setPadding(1f);
			cell.setHorizontalAlignment(label.equals(labels[0]) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
			table.addCell(cell);
		}
	}

	/** The bill line: when it was taken, how long owed, paid and still owing. */
	private void billRow(PdfPTable table, Map<String, Object> row, String billCode) {
		cell(table, billCode + "  " + text(row.get("BILL_DATE")), BOLD, Element.ALIGN_LEFT, Rectangle.TOP);
		cell(table, text(row.get("DAYS_OWING")), BOLD, Element.ALIGN_RIGHT, Rectangle.TOP);
		cell(table, money(row.get("PAID")), BOLD, Element.ALIGN_RIGHT, Rectangle.TOP);
		cell(table, money(row.get("OWING")), BOLD, Element.ALIGN_RIGHT, Rectangle.TOP);
	}

	/**
	 * One product from that bill, indented under it.
	 *
	 * The quantity sits with the product rather than in its own column: the
	 * columns to the right belong to the bill, and a number under the DAYS
	 * heading reads as a number of days.
	 */
	private void itemRow(PdfPTable table, String item, String qty) {
		// Four separate cells rather than one spanning the row: a spanning cell
		// left the table with no rows iText would lay out, and the document
		// came out with no pages at all.
		String line = "   " + item + (qty.isEmpty() ? "" : "  x" + qty);
		cell(table, line, PLAIN, Element.ALIGN_LEFT, Rectangle.NO_BORDER);
		cell(table, "", PLAIN, Element.ALIGN_RIGHT, Rectangle.NO_BORDER);
		cell(table, "", PLAIN, Element.ALIGN_RIGHT, Rectangle.NO_BORDER);
		cell(table, "", PLAIN, Element.ALIGN_RIGHT, Rectangle.NO_BORDER);
	}

	private void cell(PdfPTable table, String value, Font font, int alignment, int border) {
		PdfPCell cell = new PdfPCell(new Phrase(value, font));
		cell.setHorizontalAlignment(alignment);
		cell.setBorder(border);
		cell.setPadding(1f);
		table.addCell(cell);
	}

	/**
	 * The total, and how much of it has been owed how long.
	 *
	 * Each bill's figures repeat against its items, so the totals are taken one
	 * bill at a time rather than by summing the rows.
	 */
	private void addSummary(Document document, List<Map<String, Object>> rows) throws DocumentException {
		Map<String, BigDecimal> owingPerBill = new LinkedHashMap<>();
		Map<String, Integer> daysPerBill = new LinkedHashMap<>();

		for (Map<String, Object> row : rows) {
			String billCode = text(row.get("BILL_CODE"));
			if (owingPerBill.containsKey(billCode)) {
				continue;
			}
			owingPerBill.put(billCode, amount(row.get("OWING")));
			daysPerBill.put(billCode, (int) amount(row.get("DAYS_OWING")).doubleValue());
		}

		BigDecimal total = BigDecimal.ZERO;
		BigDecimal upTo30 = BigDecimal.ZERO;
		BigDecimal upTo90 = BigDecimal.ZERO;
		BigDecimal beyond90 = BigDecimal.ZERO;
		int oldest = 0;

		for (Map.Entry<String, BigDecimal> bill : owingPerBill.entrySet()) {
			BigDecimal owing = bill.getValue();
			int days = daysPerBill.get(bill.getKey());
			total = total.add(owing);
			oldest = Math.max(oldest, days);

			if (days <= 30) {
				upTo30 = upTo30.add(owing);
			} else if (days <= 90) {
				upTo90 = upTo90.add(owing);
			} else {
				beyond90 = beyond90.add(owing);
			}
		}

		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new int[] { 4, 3 });

		summaryRow(table, "TOTAL OWING", MONEY.format(total), true, Rectangle.TOP);
		summaryRow(table, "Bills outstanding", String.valueOf(owingPerBill.size()), false,
				Rectangle.NO_BORDER);
		summaryRow(table, "Oldest debt", oldest + " days", false, Rectangle.NO_BORDER);
		summaryRow(table, "Owed 30 days or less", MONEY.format(upTo30), false, Rectangle.TOP);
		summaryRow(table, "Owed 31 to 90 days", MONEY.format(upTo90), false, Rectangle.NO_BORDER);
		summaryRow(table, "Owed over 90 days", MONEY.format(beyond90), false, Rectangle.NO_BORDER);

		BigDecimal limit = amount(first(rows, "CREDIT_LIMIT"));
		if (limit.signum() > 0) {
			summaryRow(table, "Credit limit", MONEY.format(limit), false, Rectangle.TOP);
			summaryRow(table, "Credit still available",
					MONEY.format(amount(first(rows, "CREDIT_LIMIT_LEFT"))), false, Rectangle.NO_BORDER);
		}

		document.add(table);
	}

	private void summaryRow(PdfPTable table, String label, String value, boolean emphasise, int border) {
		Font font = emphasise ? BOLD : PLAIN;
		cell(table, label, font, Element.ALIGN_LEFT, border);
		cell(table, value, font, Element.ALIGN_RIGHT, border);
	}

	private Object first(List<Map<String, Object>> rows, String column) {
		return ObjectUtils.isEmpty(rows) ? null : rows.get(0).get(column);
	}

	private String text(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	private String money(Object value) {
		return MONEY.format(amount(value));
	}

	/** Report values arrive as whatever JDBC produced, so parse rather than cast. */
	private BigDecimal amount(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		try {
			return new BigDecimal(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}


	// ---------------------------------------------------------------------
	// Letterhead and footer.
	//
	// Copied from AccountReceivablesReceiptPdf rather than shared. Fifteen
	// generators in this package each carry their own private addHeader, and
	// they have diverged from one another - pulling them into one shared
	// method would change the output of fifteen reports at once, which is a
	// separate piece of work from adding a statement. Following the pattern
	// that is here keeps this change to the one report it is about.
	//
	// The copy is taken from the receipt deliberately: the statement reuses
	// that report's HEADER_CONTENT, so a customer sees the same letterhead on
	// both documents.
	// ---------------------------------------------------------------------
	private void addHeader(PdfWriter writer, Document document, ReportsMappingModel model) {  
		PdfPTable header = new PdfPTable(1);
		try {
			header.setTotalWidth(Utilities.millimetersToPoints(80));

			/*
			 * // add text PdfPCell leftContent = new PdfPCell();
			 * leftContent.setBorder(Rectangle.NO_BORDER);
			 */

			PdfPCell centerContent = new PdfPCell();
			centerContent.setBorder(Rectangle.NO_BORDER);

			/*
			 * PdfPCell rightContent = new PdfPCell();
			 * rightContent.setBorder(Rectangle.NO_BORDER);
			 */

			String headerContent = model.getHeaderContent();
			if(!ObjectUtils.isEmpty(headerContent)) {
				HeaderFooterContentDto contentDto = (HeaderFooterContentDto) JsonUtility.jsonToObject(headerContent,HeaderFooterContentDto.class);				
				/*
				 * for(HeaderFooterContentDetailsDto dto:contentDto.getLeftContent()) {
				 * leftContent.addElement(new Phrase(dto.getText(),
				 * FontFactory.getFont(dto.getFontName(), dto.getSize())));
				 * 
				 * }
				 */
				for(HeaderFooterContentDetailsDto dto:contentDto.getCenterContent()) {		
					Paragraph phrase=new Paragraph(dto.getText(), FontFactory.getFont(dto.getFontName(), dto.getSize(),Font.BOLD)); 
					phrase.setAlignment(1); 
					centerContent.addElement(phrase);					
				}

				/*
				 * for(HeaderFooterContentDetailsDto dto:contentDto.getRightContent()) {
				 * rightContent.addElement(new Phrase(dto.getText(),
				 * FontFactory.getFont(dto.getFontName(), dto.getSize())));
				 * 
				 * }
				 */

			}
			///header.addCell(leftContent);
			header.addCell(centerContent);
			//header.addCell(rightContent);
			header.setSpacingAfter(10);

			PdfPTable title = new PdfPTable(1);
			title.setTotalWidth(Utilities.millimetersToPoints(80));
			//title.setWidthPercentage(50);

			Font bold = new Font(FontFamily.HELVETICA,7, Font.BOLD);
			PdfPCell titleCell = new PdfPCell(new Phrase(model.getTitle(), bold)); 
			titleCell.setColspan(3);
			titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
			titleCell.setVerticalAlignment(Element.ALIGN_CENTER);
			titleCell.setBorder(0);
			title.setSpacingAfter(10);
			title.addCell(titleCell);


			PdfPTable finalFeader = new PdfPTable(1);
			finalFeader.getDefaultCell().setBorder(0);
			header.getDefaultCell().setBorder(0);
			title.getDefaultCell().setBorder(0);

			finalFeader.setTotalWidth(Utilities.millimetersToPoints(80));
			finalFeader.addCell(header);
			finalFeader.addCell(title);

			document.add(finalFeader);

		} catch (DocumentException de) {
			throw new ExceptionConverter(de);
		} catch (Exception e) {
			throw new ExceptionConverter(e);
		}
	}

	private void addFooter(PdfWriter writer, ReportsMappingModel model,Document document) {
		PdfPTable footer = new PdfPTable(1);
		try {
			footer.setTotalWidth(Utilities.millimetersToPoints(80));
			PdfPCell centerContent = new PdfPCell();
			centerContent.setBorder(Rectangle.NO_BORDER);

			String footerContent = model.getFooterContent();
			if(!ObjectUtils.isEmpty(footerContent)) {
				HeaderFooterContentDto contentDto = (HeaderFooterContentDto) JsonUtility.jsonToObject(footerContent,HeaderFooterContentDto.class);				
				for(HeaderFooterContentDetailsDto dto:contentDto.getCenterContent()) {		
					Paragraph phrase=new Paragraph(dto.getText(), FontFactory.getFont(dto.getFontName(), dto.getSize(),Font.BOLD));
					phrase.setAlignment(1); 
					centerContent.addElement(phrase);
				}
			}
			footer.addCell(centerContent);
			centerContent.setHorizontalAlignment(Element.ALIGN_CENTER);
			centerContent.setVerticalAlignment(Element.ALIGN_CENTER);

			footer.getDefaultCell().setBorder(0);
			document.add(footer);
		}  catch (Exception e) {
			throw new ExceptionConverter(e);
		}
	}

}
