package com.sevis.ordersservice.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InvoicePdfParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Parsed result records ─────────────────────────────────────────────────

    public record LineItem(
            int lineNumber, String hsnCode, String partNumber, String description,
            String type, double quantity, double rate, double baseAmount,
            double discountAmount, double taxableAmount,
            double cgstRate, double cgstAmount,
            double sgstRate, double sgstAmount,
            double totalAmount) {}

    public record ParsedInvoice(
            String invoiceNumber, LocalDate invoiceDate,
            String customerName, String customerPhone,
            String customerAddress, String customerCity, String customerState, String customerPinCode,
            String vehicleRegNo, String chassisNo, String vehicleModel, int kms,
            String originalJobCardNumber, LocalDate jobCardDate,
            String serviceType, String paymentMethod,
            String dealerGstin, String dealerPan, String dealerName,
            String placeOfSupply, String preparedBy,
            double partsNetTaxableAmount, double totalTaxAmount,
            double grandTotal, double adjustments,
            List<LineItem> lineItems) {}

    // ── Public entry point ────────────────────────────────────────────────────

    public static ParsedInvoice parse(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return parseText(text);
        }
    }

    // ── Field extraction ──────────────────────────────────────────────────────

    private static ParsedInvoice parseText(String text) {
        return new ParsedInvoice(
                extract(text, "Invoice\\s+No[:\\s]+([A-Z0-9]+)"),
                parseDate(extract(text, "Invoice\\s+Date[:\\s]+(\\d{2}/\\d{2}/\\d{4})")),
                cleanName(extract(text, "Name[:\\s]+(?:Mr\\.?\\s*|Mrs\\.?\\s*|Ms\\.?\\s*)?([^\\n]+)")),
                extractPhone(text),
                extract(text, "(?:Address|VILL)[^\\n]*[:\\s]+([^\\n]+)"),
                extract(text, "City[:\\s]+([^,\\n]+)"),
                extractState(text),
                extract(text, "Postal\\s+Code[:\\s]+(\\d{6})"),
                extract(text, "Vehicle\\s+Regn\\.?\\s*No[:\\s]+([A-Z0-9]+)"),
                extract(text, "Chassis\\s+No[:\\s]+([A-Z0-9]+)"),
                extract(text, "Model[:\\s]+([^\\n]+)"),
                parseIntSafe(extract(text, "Kms[:\\s]+(\\d+)")),
                extract(text, "Job\\s+Card\\s+No[:\\s]+([A-Za-z0-9-]+)"),
                parseDate(extract(text, "Job\\s+Card\\s+Date[:\\s]+(\\d{2}/\\d{2}/\\d{4})")),
                extract(text, "Service\\s+Request\\s+Type[:\\s]+([^\\n]+)"),
                extract(text, "Payment\\s+Method[:\\s]+([A-Z]+)"),
                extract(text, "Dealer\\s+GSTIN[:\\s]+([0-9A-Z]+)"),
                extract(text, "Dealer\\s+PAN[:\\s]+([A-Z0-9]+)"),
                extractDealerName(text),
                extract(text, "Place\\s+of\\s+Supply[:\\s]+([^\\n]+)"),
                extract(text, "Prepared\\s+By[:\\s]+([^\\n]+)"),
                parseDoubleSafe(extract(text, "Parts\\s+Net\\s+Taxable\\s+Amount[:\\s]+([\\d,]+\\.\\d+)")),
                parseDoubleSafe(extract(text, "Total\\s+Tax\\s+Amount[:\\s]+([\\d,]+\\.\\d+)")),
                parseDoubleSafe(extract(text, "Grand\\s+Total[:\\s]+([\\d,]+\\.\\d+)")),
                parseDoubleSafe(extract(text, "Adjustments[:\\s]+([\\d,]+\\.\\d+)")),
                parseLineItems(text)
        );
    }

    // ── Line item extraction ──────────────────────────────────────────────────

    private static final Pattern LINE_ITEM_PATTERN = Pattern.compile(
            "^(\\d{1,3})\\s+(\\d{5,8})\\s+(\\S+)\\s+(.+?)\\s+" +
            "(FREESERVICE|LABOUR|PARTS|REPLACEMENT|INSPECTION|SUBLET|SERVICE)\\s+[YN]?\\s*" +
            "([\\d.]+)\\s+([\\d.]+)\\s+([\\d.]+)\\s+" +
            "[\\d.,]+\\s+([\\d.,]+)\\s+([\\d.]+)\\s+([\\d.,]+)\\s+([\\d.]+)\\s+([\\d.,]+)\\s+([\\d.,]+)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    private static List<LineItem> parseLineItems(String text) {
        List<LineItem> items = new ArrayList<>();
        Matcher m = LINE_ITEM_PATTERN.matcher(text);
        while (m.find()) {
            try {
                items.add(new LineItem(
                        Integer.parseInt(m.group(1)),
                        m.group(2),
                        m.group(3),
                        m.group(4).trim(),
                        m.group(5).toUpperCase(),
                        parseDoubleSafe(m.group(6)),
                        parseDoubleSafe(m.group(7)),
                        parseDoubleSafe(m.group(8)),
                        parseDoubleSafe(m.group(9)),
                        parseDoubleSafe(m.group(10)),
                        parseDoubleSafe(m.group(11)),
                        parseDoubleSafe(m.group(12)),
                        parseDoubleSafe(m.group(13)),
                        parseDoubleSafe(m.group(14)),
                        parseDoubleSafe(m.group(15))
                ));
            } catch (Exception ignored) {}
        }
        return items;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extract(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String extractPhone(String text) {
        // "Phone No (Res, Off, Mob): , , 6206684038" — take last non-empty token
        Matcher m = Pattern.compile("Phone\\s+No[^:]*:[^\\d]*((?:\\d{10}[,\\s]*)+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!m.find()) return null;
        String[] parts = m.group(1).split("[,\\s]+");
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i].trim();
            if (p.matches("\\d{10}")) return p;
        }
        return null;
    }

    private static String extractState(String text) {
        Matcher m = Pattern.compile("State[:\\s]+([A-Za-z ]+?)(?:\\(\\d+\\))?(?:\\n|$)", Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String extractDealerName(String text) {
        // "For: JHARKHAND DHAM MOTORS" or appears near GSTIN block
        Matcher m = Pattern.compile("For[:\\s]+([A-Z ]+MOTORS[A-Z ]*)", Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String cleanName(String name) {
        if (name == null) return null;
        return name.replaceAll("^(Mr\\.?|Mrs\\.?|Ms\\.?)\\s*", "").trim();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), DATE_FMT); } catch (DateTimeParseException e) { return null; }
    }

    private static double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (NumberFormatException e) { return 0.0; }
    }

    private static int parseIntSafe(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}
