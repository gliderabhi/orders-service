package com.sevis.ordersservice.service;

import com.sevis.ordersservice.model.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class JobCardPdfGenerator {

    // A4 portrait
    private static final float W  = 595f;
    private static final float H  = 842f;
    private static final float M  = 36f;
    private static final float CW = W - 2 * M; // 523

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Labour table: Sl | Description | Type | Qty | Rate | Amount
    private static final float[] LC = {20, 200, 65, 28, 105, 105};
    private static final String[] LH = {"Sl", "Description", "Type", "Qty", "Rate", "Amount"};

    // Parts table: Sl | Part No | Description | Type | Qty | Unit Price | Total
    private static final float[] PC = {20, 75, 160, 58, 28, 91, 91};
    private static final String[] PH = {"Sl", "Part No", "Description", "Type", "Qty", "Unit Price", "Total"};

    // Ancillary: Sl | Description | Amount
    private static final float[] AC = {20, 393, 110};
    private static final String[] AH = {"Sl", "Description", "Amount"};

    private static final float ROW  = 13f;
    private static final float THDR = 16f;

    private static final Color DARK  = new Color(30, 30, 30);
    private static final Color BLUE  = new Color(30, 60, 120);
    private static final Color LGRAY = new Color(210, 210, 210);
    private static final Color BGALT = new Color(248, 248, 252);
    private static final Color MUTED = new Color(90, 90, 90);

    public static byte[] generate(JobCard jc) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font reg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = H - M;

                // ── Title bar ─────────────────────────────────────────────────
                fill(cs, M, y - 26, CW, 26, new Color(40, 40, 40));
                textCenter(cs, bold, 13, "SERVICE BILL", W / 2f, y - 19, Color.WHITE);
                y -= 32;

                // ── Job card reference row ─────────────────────────────────────
                float q = CW / 3f;
                inlineKv(cs, bold, reg, M,       y, "Job Card No:", jc.getJobCardNumber(),           q - 6);
                inlineKv(cs, bold, reg, M + q,   y, "Date In:",
                        jc.getDateIn()  != null ? jc.getDateIn().format(DATE_FMT) : "-",          q - 6);
                inlineKv(cs, bold, reg, M + q*2, y, "Service:",
                        jc.getServiceType() != null ? jc.getServiceType().replace("_", " ") : "-", q - 6);
                y -= 14;
                hline(cs, y, M, CW, 0.5f, LGRAY); y -= 8;

                // ── Customer | Vehicle ─────────────────────────────────────────
                float secY = y;
                text(cs, bold, 7, "CUSTOMER", M, y, BLUE);             y -= 12;
                Customer cust = jc.getCustomer();
                if (cust != null) {
                    if (nb(cust.getName()))    { text(cs, reg, 8, cust.getName(), M, y, DARK);             y -= 11; }
                    if (nb(cust.getPhone()))   { text(cs, reg, 7, "Ph: " + cust.getPhone(), M, y, MUTED);  y -= 10; }
                    if (nb(cust.getAddress())) { text(cs, reg, 7, trunc(cust.getAddress(), 40), M, y, MUTED); y -= 10; }
                }

                float vx = M + CW * 0.5f;
                float vy = secY;
                text(cs, bold, 7, "VEHICLE", vx, vy, BLUE);             vy -= 12;
                Vehicle v = jc.getVehicle();
                if (v != null) {
                    vy = kv(cs, bold, reg, vx, vy, "Reg No:", v.getRegNumber());
                    String mm = ((v.getMake() != null ? v.getMake() : "") + " " + (v.getModel() != null ? v.getModel() : "")).trim();
                    if (!mm.isEmpty()) vy = kv(cs, bold, reg, vx, vy, "Model:", mm);
                    if (nb(v.getChassisNo())) vy = kv(cs, bold, reg, vx, vy, "Chassis:", v.getChassisNo());
                    if (jc.getKmIn() > 0)     vy = kv(cs, bold, reg, vx, vy, "KMS:", String.valueOf(jc.getKmIn()));
                }

                y = Math.min(y, vy) - 8;
                hline(cs, y, M, CW, 0.5f, LGRAY); y -= 8;

                // ── Advisor / complaint row ────────────────────────────────────
                if (nb(jc.getAdvisorName()) || nb(jc.getCustomerComplaint())) {
                    if (nb(jc.getAdvisorName()))
                        inlineKv(cs, bold, reg, M, y, "Advisor:", jc.getAdvisorName(), CW / 2f - 6);
                    if (nb(jc.getCustomerComplaint()))
                        inlineKv(cs, bold, reg, M + CW * 0.5f, y, "Complaint:", trunc(jc.getCustomerComplaint(), 50), CW * 0.5f - 6);
                    y -= 14;
                    hline(cs, y, M, CW, 0.5f, LGRAY); y -= 8;
                }

                // ── Labour items ───────────────────────────────────────────────
                if (!jc.getLabourItems().isEmpty()) {
                    text(cs, bold, 8, "LABOUR / WORK DONE", M, y, BLUE); y -= 10;
                    y = drawSimpleTable(cs, jc.getLabourItems().stream().map(l -> new String[]{
                            str(jc.getLabourItems().indexOf(l) + 1),
                            s(l.getDescription()), s(l.getType()),
                            String.valueOf(l.getQuantity()), fv(l.getRate()), fv(l.getAmount())
                    }).toList(), LC, LH, bold, reg, y, new int[]{3, 4, 5});
                    y -= 6;
                }

                // ── Parts ──────────────────────────────────────────────────────
                if (!jc.getParts().isEmpty()) {
                    text(cs, bold, 8, "PARTS USED", M, y, BLUE); y -= 10;
                    y = drawSimpleTable(cs, jc.getParts().stream().map(p -> new String[]{
                            str(jc.getParts().indexOf(p) + 1),
                            s(p.getPartNumber()), s(p.getDescription()), s(p.getPartType()),
                            String.valueOf(p.getQuantity()), fv(p.getUnitPrice()), fv(p.getTotalPrice())
                    }).toList(), PC, PH, bold, reg, y, new int[]{4, 5, 6});
                    y -= 6;
                }

                // ── Ancillary ──────────────────────────────────────────────────
                if (!jc.getAncillaryItems().isEmpty()) {
                    text(cs, bold, 8, "ANCILLARY SERVICES", M, y, BLUE); y -= 10;
                    y = drawSimpleTable(cs, jc.getAncillaryItems().stream().map(a -> new String[]{
                            str(jc.getAncillaryItems().indexOf(a) + 1),
                            s(a.getDescription()), fv(a.getAmount())
                    }).toList(), AC, AH, bold, reg, y, new int[]{2});
                    y -= 6;
                }

                // ── Billing summary ────────────────────────────────────────────
                JobCardBilling b = jc.getBilling();
                if (b != null) {
                    hline(cs, y, M, CW, 0.5f, LGRAY); y -= 8;
                    float tx = M + CW * 0.55f;
                    float tw = CW - (tx - M);
                    if (b.getLabourTotal()    > 0) { totRow(cs, reg,  tx, tw, y, "Labour Total:",     fv(b.getLabourTotal()));    y -= 12; }
                    if (b.getPartsTotal()     > 0) { totRow(cs, reg,  tx, tw, y, "Parts Total:",      fv(b.getPartsTotal()));     y -= 12; }
                    if (b.getAncillaryTotal() > 0) { totRow(cs, reg,  tx, tw, y, "Ancillary Total:",  fv(b.getAncillaryTotal())); y -= 12; }
                    if (b.getDiscount()       > 0) { totRow(cs, reg,  tx, tw, y, "Discount:",        "-" + fv(b.getDiscount())); y -= 12; }
                    totRow(cs, reg,  tx, tw, y, "Taxable Amount:",    fv(b.getTaxableAmount())); y -= 12;
                    if (b.getCgstRate() > 0) { totRow(cs, reg, tx, tw, y, "CGST " + fp(b.getCgstRate()) + "%:", fv(b.getCgstAmount())); y -= 12; }
                    if (b.getSgstRate() > 0) { totRow(cs, reg, tx, tw, y, "SGST " + fp(b.getSgstRate()) + "%:", fv(b.getSgstAmount())); y -= 12; }
                    if (b.getIgstRate() > 0) { totRow(cs, reg, tx, tw, y, "IGST " + fp(b.getIgstRate()) + "%:", fv(b.getIgstAmount())); y -= 12; }
                    hline(cs, y, tx, tw, 0.7f, DARK); y -= 4;
                    totRow(cs, bold, tx, tw, y, "GRAND TOTAL:", "Rs. " + fv(b.getGrandTotal())); y -= 14;
                    if (b.getAdvanceAmount() > 0) { totRow(cs, reg, tx, tw, y, "Advance Paid:", fv(b.getAdvanceAmount())); y -= 12; }
                    totRow(cs, bold, tx, tw, y, "Balance Due:", "Rs. " + fv(b.getBalanceDue())); y -= 16;
                }

                // ── Footer ────────────────────────────────────────────────────
                hline(cs, M + 14, M, CW, 0.4f, LGRAY);
                text(cs, reg, 7, "Computer generated. Job Card: " + jc.getJobCardNumber(), M, M + 6, MUTED);
                text(cs, bold, 7, "Authorized Signatory", M + CW - 90, M + 6, DARK);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Generic table drawer ──────────────────────────────────────────────────

    private static float drawSimpleTable(PDPageContentStream cs,
            java.util.List<String[]> rows, float[] cols, String[] headers,
            PDType1Font bold, PDType1Font reg, float y, int[] rightAlignCols) throws IOException {

        fill(cs, M, y - THDR, sum(cols), THDR, LGRAY);
        float x = M;
        for (int i = 0; i < headers.length; i++) {
            boolean ra = contains(rightAlignCols, i);
            if (ra) textRC(cs, bold, 7, headers[i], x, cols[i] - 3, y - THDR + 5, DARK);
            else    textC(cs, bold, 7, headers[i], x + 2, y - THDR + 5, cols[i] - 4, DARK);
            x += cols[i];
        }
        y -= THDR;
        hline(cs, y, M, sum(cols), 0.5f, new Color(140, 140, 140));

        boolean alt = false;
        for (String[] row : rows) {
            if (alt) fill(cs, M, y - ROW, sum(cols), ROW, BGALT);
            alt = !alt;
            x = M;
            for (int i = 0; i < row.length && i < cols.length; i++) {
                float base = y - ROW + 4;
                boolean ra = contains(rightAlignCols, i);
                if (ra) textRC(cs, reg, 7, row[i], x, cols[i] - 3, base, DARK);
                else    textC(cs, reg, 7, row[i], x + 2, base, cols[i] - 4, DARK);
                x += cols[i];
            }
            y -= ROW;
            hline(cs, y, M, sum(cols), 0.2f, new Color(210, 210, 210));
        }
        hline(cs, y, M, sum(cols), 0.6f, new Color(100, 100, 100));
        return y;
    }

    // ── Drawing primitives ────────────────────────────────────────────────────

    private static void text(PDPageContentStream cs, PDType1Font f, float sz,
            String t, float x, float y, Color c) throws IOException {
        if (t == null || t.isBlank()) return;
        cs.setNonStrokingColor(c);
        cs.beginText(); cs.setFont(f, sz); cs.newLineAtOffset(x, y); cs.showText(safe(t)); cs.endText();
    }

    private static void textCenter(PDPageContentStream cs, PDType1Font f, float sz,
            String t, float cx, float y, Color c) throws IOException {
        float w = f.getStringWidth(safe(t)) / 1000f * sz;
        text(cs, f, sz, t, cx - w / 2f, y, c);
    }

    private static void textC(PDPageContentStream cs, PDType1Font f, float sz,
            String t, float x, float y, float maxW, Color c) throws IOException {
        if (t == null || t.isBlank()) return;
        String s = safe(t);
        while (s.length() > 1 && f.getStringWidth(s) / 1000f * sz > maxW) s = s.substring(0, s.length() - 1);
        text(cs, f, sz, s, x, y, c);
    }

    private static void textRC(PDPageContentStream cs, PDType1Font f, float sz,
            String t, float colX, float colW, float y, Color c) throws IOException {
        if (t == null || t.isBlank()) return;
        String s = safe(t);
        while (s.length() > 1 && f.getStringWidth(s) / 1000f * sz > colW - 4) s = s.substring(0, s.length() - 1);
        float w = f.getStringWidth(s) / 1000f * sz;
        text(cs, f, sz, s, colX + colW - 2 - w, y, c);
    }

    private static void textRight(PDPageContentStream cs, PDType1Font f, float sz,
            String t, float xRight, float y, Color c) throws IOException {
        if (t == null || t.isBlank()) return;
        float w = f.getStringWidth(safe(t)) / 1000f * sz;
        text(cs, f, sz, t, xRight - w, y, c);
    }

    private static void fill(PDPageContentStream cs, float x, float y, float w, float h, Color c) throws IOException {
        cs.setNonStrokingColor(c); cs.addRect(x, y, w, h); cs.fill();
    }

    private static void hline(PDPageContentStream cs, float y, float x, float w, float lw, Color c) throws IOException {
        cs.setStrokingColor(c); cs.setLineWidth(lw); cs.moveTo(x, y); cs.lineTo(x + w, y); cs.stroke();
    }

    private static float kv(PDPageContentStream cs, PDType1Font bold, PDType1Font reg,
            float x, float y, String key, String val) throws IOException {
        text(cs, bold, 7, key, x, y, MUTED);
        float kw = bold.getStringWidth(safe(key) + " ") / 1000f * 7;
        text(cs, reg, 8, val, x + kw, y, DARK);
        return y - 11;
    }

    private static void inlineKv(PDPageContentStream cs, PDType1Font bold, PDType1Font reg,
            float x, float y, String key, String val, float maxW) throws IOException {
        text(cs, bold, 7, key, x, y, MUTED);
        float kw = bold.getStringWidth(safe(key) + " ") / 1000f * 7;
        textC(cs, reg, 8, val, x + kw, y, maxW - kw, DARK);
    }

    private static void totRow(PDPageContentStream cs, PDType1Font f,
            float x, float w, float y, String label, String val) throws IOException {
        text(cs, f, 8, label, x + 4, y, DARK);
        textRight(cs, f, 8, val, x + w - 4, y, DARK);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float sum(float[] arr) { float s = 0; for (float v : arr) s += v; return s; }
    private static boolean contains(int[] arr, int v) { for (int i : arr) if (i == v) return true; return false; }

    private static String fv(double d) { if (d == 0) return ""; return String.format(Locale.US, "%,.2f", d); }
    private static String fp(double d) { return String.format(Locale.US, "%.1f", d); }
    private static String str(int i)   { return String.valueOf(i); }
    private static String s(String v)  { return v == null ? "" : v; }
    private static boolean nb(String v){ return v != null && !v.isBlank(); }
    private static String trunc(String v, int max) {
        if (v == null) return "";
        return v.length() <= max ? v : v.substring(0, max - 1) + ".";
    }
    private static String safe(String s) {
        if (s == null) return "";
        return s.chars().filter(c -> c >= 0x20 && c <= 0xFF)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
