package com.sevis.ordersservice.service;

import com.sevis.ordersservice.model.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvoicePdfGenerator {

    // A4 landscape
    private static final float W  = 842f;
    private static final float H  = 595f;
    private static final float M  = 36f;
    private static final float CW = W - 2 * M; // 770

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Table column widths — must sum to 770
    private static final float[] COL = {22, 46, 68, 160, 56, 26, 48, 48, 34, 52, 26, 42, 26, 42, 74};
    private static final String[] COL_HDR = {
        "Sl", "HSN", "Part No", "Description", "Type",
        "Qty", "Rate", "Base Amt", "Disc", "Taxable",
        "C%", "CGST", "S%", "SGST", "Total"
    };

    private static final float ROW_H  = 13f;
    private static final float THDR_H = 16f;

    private static final Color DARK  = new Color(30,  30,  30);
    private static final Color BLUE  = new Color(30,  60, 120);
    private static final Color LGRAY = new Color(210, 210, 210);
    private static final Color BGALT = new Color(248, 248, 252);
    private static final Color MUTED = new Color(90,  90,  90);

    public static byte[] generate(Invoice inv) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font reg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = H - M;

                // ── Title bar ─────────────────────────────────────────────────
                fill(cs, M, y - 26, CW, 26, new Color(40, 40, 40));
                textCenter(cs, bold, 13, "TAX INVOICE", W / 2f, y - 19, Color.WHITE);
                y -= 32;

                // ── Header: dealer info (left) | invoice details (right) ──────
                float headerTopY = y;
                String dealerName = coalesce(inv.getDealerName(), "Authorized Dealer");
                text(cs, bold, 10, dealerName, M, y, DARK);       y -= 13;
                if (notBlank(inv.getDealerGstin())) { text(cs, reg, 8, "GSTIN: " + inv.getDealerGstin(), M, y, MUTED); y -= 11; }
                if (notBlank(inv.getDealerPan()))   { text(cs, reg, 8, "PAN:   " + inv.getDealerPan(),   M, y, MUTED); y -= 11; }

                float ry = headerTopY;
                float rx = M + CW * 0.55f;
                ry = kv(cs, bold, reg, rx, ry, "Invoice No:",    coalesce(inv.getInvoiceNumber(), "-"));
                ry = kv(cs, bold, reg, rx, ry, "Invoice Date:",  inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(DATE_FMT) : "-");
                if (notBlank(inv.getPlaceOfSupply())) ry = kv(cs, bold, reg, rx, ry, "Place of Supply:", inv.getPlaceOfSupply());
                if (notBlank(inv.getPaymentMethod())) ry = kv(cs, bold, reg, rx, ry, "Payment Method:",  inv.getPaymentMethod());

                y = Math.min(y, ry) - 8;
                hline(cs, y, M, CW, 0.6f, LGRAY);
                y -= 8;

                // ── Customer (left) | Vehicle (right) ────────────────────────
                float secY = y;
                text(cs, bold, 7, "CUSTOMER DETAILS", M, y, BLUE);
                y -= 12;
                Customer cust = inv.getCustomer();
                if (cust != null) {
                    if (notBlank(cust.getName()))    { text(cs, reg, 8, cust.getName(), M, y, DARK);             y -= 11; }
                    if (notBlank(cust.getPhone()))   { text(cs, reg, 8, "Ph: " + cust.getPhone(), M, y, MUTED);  y -= 11; }
                    if (notBlank(cust.getAddress())) {
                        for (String line : wordWrap(cust.getAddress(), 50)) {
                            text(cs, reg, 7, line, M, y, MUTED);
                            y -= 10;
                        }
                    }
                }

                float vx = M + CW * 0.5f;
                float vy = secY;
                text(cs, bold, 7, "VEHICLE DETAILS", vx, vy, BLUE);
                vy -= 12;
                if (notBlank(inv.getVehicleRegNo())) vy = kv(cs, bold, reg, vx, vy, "Reg No:",  inv.getVehicleRegNo());
                if (notBlank(inv.getChassisNo()))    vy = kv(cs, bold, reg, vx, vy, "Chassis:", inv.getChassisNo());
                JobCard jc = inv.getJobCard();
                if (jc != null && jc.getVehicle() != null) {
                    Vehicle v = jc.getVehicle();
                    String mm = (coalesce(v.getMake(), "") + " " + coalesce(v.getModel(), "")).trim();
                    if (!mm.isBlank()) vy = kv(cs, bold, reg, vx, vy, "Model:", mm);
                    if (notBlank(v.getVariant())) vy = kv(cs, bold, reg, vx, vy, "Variant:", v.getVariant());
                }
                if (inv.getKms() != null && inv.getKms() > 0)
                    vy = kv(cs, bold, reg, vx, vy, "KMS:", String.valueOf(inv.getKms()));

                y = Math.min(y, vy) - 8;
                hline(cs, y, M, CW, 0.6f, LGRAY);
                y -= 8;

                // ── Job card info row ─────────────────────────────────────────
                String jcNo   = notBlank(inv.getOriginalJobCardNumber()) ? inv.getOriginalJobCardNumber()
                              : (jc != null ? jc.getJobCardNumber() : "-");
                String jcDate = inv.getJobCardDate() != null ? inv.getJobCardDate().format(DATE_FMT)
                              : (jc != null && jc.getDateIn() != null ? jc.getDateIn().format(DATE_FMT) : "-");
                String svc    = notBlank(inv.getServiceType()) ? inv.getServiceType().replace("_", " ")
                              : (jc != null && notBlank(jc.getServiceType()) ? jc.getServiceType().replace("_", " ") : "-");
                float q = CW / 4f;
                inlineKv(cs, bold, reg, M,           y, "Job Card No:", jcNo,   q - 6);
                inlineKv(cs, bold, reg, M + q,       y, "JC Date:",     jcDate, q - 6);
                inlineKv(cs, bold, reg, M + q * 2f,  y, "Service:",     svc,    q - 6);
                if (notBlank(inv.getPreparedBy()))
                    inlineKv(cs, bold, reg, M + q * 3f, y, "Prepared By:", inv.getPreparedBy(), q - 6);
                y -= 14;
                hline(cs, y, M, CW, 0.6f, LGRAY);
                y -= 6;

                // ── Line items table ──────────────────────────────────────────
                y = drawTable(cs, inv.getLineItems(), bold, reg, y);

                // ── Totals ────────────────────────────────────────────────────
                y -= 6;
                float tx = M + CW * 0.62f;
                float tw = CW - (tx - M);

                if (gt0(inv.getPartsNetTaxableAmount())) {
                    totalRow(cs, reg,  tx, tw, y, "Parts Net Taxable Amount:", fmt(inv.getPartsNetTaxableAmount())); y -= 12;
                }
                if (gt0(inv.getTotalTaxAmount())) {
                    totalRow(cs, reg,  tx, tw, y, "Total Tax Amount:",         fmt(inv.getTotalTaxAmount()));        y -= 12;
                }
                if (inv.getAdjustments() != null && inv.getAdjustments() != 0) {
                    totalRow(cs, reg,  tx, tw, y, "Adjustments:",              fmt(inv.getAdjustments()));          y -= 12;
                }
                hline(cs, y, tx, tw, 0.7f, DARK);
                y -= 4;
                totalRow(cs, bold, tx, tw, y, "GRAND TOTAL:", "Rs. " + fmt(inv.getGrandTotal())); y -= 16;

                // ── Footer ────────────────────────────────────────────────────
                hline(cs, M + 14, M, CW, 0.4f, LGRAY);
                text(cs, reg, 7, "Computer generated invoice. No signature required.", M, M + 6, new Color(150, 150, 150));
                textRight(cs, bold, 7, "For " + dealerName, M + CW, M + 6, DARK);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private static float drawTable(PDPageContentStream cs,
            List<InvoiceLineItem> items, PDType1Font bold, PDType1Font reg, float y) throws IOException {
        // Header background
        fill(cs, M, y - THDR_H, CW, THDR_H, LGRAY);
        float x = M;
        for (int i = 0; i < COL.length; i++) {
            if (i >= 5) textRightClip(cs, bold, 7, COL_HDR[i], x, COL[i] - 3, y - THDR_H + 5, DARK);
            else        textClip(cs, bold, 7, COL_HDR[i], x + 2, y - THDR_H + 5, COL[i] - 4, DARK);
            x += COL[i];
        }
        y -= THDR_H;
        hline(cs, y, M, CW, 0.5f, new Color(140, 140, 140));

        boolean alt = false;
        for (InvoiceLineItem li : items) {
            if (alt) fill(cs, M, y - ROW_H, CW, ROW_H, BGALT);
            alt = !alt;

            x = M;
            String[] vals = {
                str(li.getLineNumber()),    s(li.getHsnCode()),     s(li.getPartNumber()),
                s(li.getDescription()),     s(li.getType()),
                fq(li.getQuantity()),       fv(li.getRate()),        fv(li.getBaseAmount()),
                fv(li.getDiscountAmount()), fv(li.getTaxableAmount()),
                fp(li.getCgstRate()),       fv(li.getCgstAmount()),
                fp(li.getSgstRate()),       fv(li.getSgstAmount()),  fv(li.getTotalAmount())
            };
            for (int i = 0; i < vals.length; i++) {
                float rowBaseline = y - ROW_H + 4;
                if (i >= 5) textRightClip(cs, reg, 7, vals[i], x, COL[i] - 3, rowBaseline, DARK);
                else        textClip(cs, reg, 7, vals[i], x + 2, rowBaseline, COL[i] - 4, DARK);
                x += COL[i];
            }
            y -= ROW_H;
            hline(cs, y, M, CW, 0.2f, new Color(210, 210, 210));
        }
        hline(cs, y, M, CW, 0.6f, new Color(100, 100, 100));
        return y;
    }

    // ── Drawing primitives ────────────────────────────────────────────────────

    private static void text(PDPageContentStream cs, PDType1Font f, float sz,
            String txt, float x, float y, Color c) throws IOException {
        if (txt == null || txt.isBlank()) return;
        cs.setNonStrokingColor(c);
        cs.beginText();
        cs.setFont(f, sz);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(txt));
        cs.endText();
    }

    private static void textCenter(PDPageContentStream cs, PDType1Font f, float sz,
            String txt, float cx, float y, Color c) throws IOException {
        if (txt == null) return;
        float w = f.getStringWidth(safe(txt)) / 1000f * sz;
        text(cs, f, sz, txt, cx - w / 2f, y, c);
    }

    private static void textRight(PDPageContentStream cs, PDType1Font f, float sz,
            String txt, float xRight, float y, Color c) throws IOException {
        if (txt == null || txt.isBlank()) return;
        float w = f.getStringWidth(safe(txt)) / 1000f * sz;
        text(cs, f, sz, txt, xRight - w, y, c);
    }

    private static void textClip(PDPageContentStream cs, PDType1Font f, float sz,
            String txt, float x, float y, float maxW, Color c) throws IOException {
        if (txt == null || txt.isBlank()) return;
        String s = safe(txt);
        while (s.length() > 1 && f.getStringWidth(s) / 1000f * sz > maxW)
            s = s.substring(0, s.length() - 1);
        text(cs, f, sz, s, x, y, c);
    }

    private static void textRightClip(PDPageContentStream cs, PDType1Font f, float sz,
            String txt, float colX, float colW, float y, Color c) throws IOException {
        if (txt == null || txt.isBlank()) return;
        String s = safe(txt);
        while (s.length() > 1 && f.getStringWidth(s) / 1000f * sz > colW - 4)
            s = s.substring(0, s.length() - 1);
        textRight(cs, f, sz, s, colX + colW - 2, y, c);
    }

    private static void fill(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private static void hline(PDPageContentStream cs, float y, float x, float w, float lw, Color c) throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(lw);
        cs.moveTo(x, y);
        cs.lineTo(x + w, y);
        cs.stroke();
    }

    // Stacked key-value pair: key on left, value to the right of key
    private static float kv(PDPageContentStream cs, PDType1Font bold, PDType1Font reg,
            float x, float y, String key, String val) throws IOException {
        text(cs, bold, 7, key, x, y, MUTED);
        float kw = bold.getStringWidth(safe(key) + " ") / 1000f * 7;
        text(cs, reg, 8, val, x + kw, y, DARK);
        return y - 11;
    }

    // Inline key-value on same line, value clipped to maxW
    private static void inlineKv(PDPageContentStream cs, PDType1Font bold, PDType1Font reg,
            float x, float y, String key, String val, float maxW) throws IOException {
        text(cs, bold, 7, key, x, y, MUTED);
        float kw = bold.getStringWidth(safe(key) + " ") / 1000f * 7;
        textClip(cs, reg, 8, val, x + kw, y, maxW - kw, DARK);
    }

    // Right-align label | right-align value
    private static void totalRow(PDPageContentStream cs, PDType1Font f,
            float x, float w, float y, String label, String val) throws IOException {
        text(cs, f, 8, label, x + 4, y, DARK);
        textRight(cs, f, 8, val, x + w - 4, y, DARK);
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private static String fmt(Double d) {
        if (d == null) return "0.00";
        return String.format(Locale.US, "%,.2f", d);
    }

    private static String fv(Double d) {
        if (d == null || d == 0.0) return "";
        return String.format(Locale.US, "%,.2f", d);
    }

    private static String fp(Double d) {
        if (d == null || d == 0.0) return "";
        return String.format(Locale.US, "%.1f", d);
    }

    private static String fq(Double d) {
        if (d == null) return "";
        if (d == Math.floor(d)) return String.valueOf(d.intValue());
        return String.format(Locale.US, "%.2f", d);
    }

    private static String str(Integer i) { return i == null ? "" : String.valueOf(i); }
    private static String s(String v)    { return v == null ? "" : v; }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static boolean notBlank(String v) { return v != null && !v.isBlank(); }
    private static boolean gt0(Double d)      { return d != null && d > 0; }

    // Strip chars outside WinAnsiEncoding (Type1 fonts) — replaces Rs symbol etc.
    private static String safe(String s) {
        if (s == null) return "";
        return s.chars()
                .filter(c -> c >= 0x20 && c <= 0xFF)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static String[] wordWrap(String text, int maxChars) {
        if (text == null) return new String[0];
        if (text.length() <= maxChars) return new String[]{text};
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() + word.length() + 1 > maxChars) {
                if (sb.length() > 0) lines.add(sb.toString().trim());
                sb = new StringBuilder(word);
            } else {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(word);
            }
        }
        if (!sb.isEmpty()) lines.add(sb.toString().trim());
        return lines.toArray(new String[0]);
    }
}
