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
    private static final float M  = 28f;
    private static final float CW = W - 2 * M;   // 786

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // 21 columns matching real dealer invoice, summing to 786
    // S No | HSN/SAC | Part#/Job Code | Particulars | Type | UoM | Float | Qty | Rate |
    // Total Amt (Base) | Extra Chg | Disc Amt/Item | Campgn Disc% | Disc% | Ins Liab% |
    // Taxable Amt | CGST% | CGST Amt | SGST% | SGST Amt | Total Amt (Incl.Tax)
    // Widths proportional: data columns wider, always-empty columns narrower
    private static final float[] COL = {
        16, 40, 50, 138, 44, 16, 16, 26, 44, 54,
        26, 30, 22, 18, 20, 54, 20, 38, 20, 38, 56
    };
    private static final String[] HDR1 = {
        "S",   "HSN/", "Part#/",   "Particulars", "Type", "UoM", "Flt", "Qty", "Rate", "Total Amt",
        "Xtra","Disc", "Cmpgn",   "Disc",  "Ins",  "Taxable", "CGS", "CGST", "SGS", "SGST", "Total Amt"
    };
    private static final String[] HDR2 = {
        "No",  "SAC",  "Job Code", "",      "",    "",    "",    "",    "",    "(Base)",
        "Chg", "Amt",  "Disc%",   "%",     "Liab","Amt",  "T%",  "Amt", "T%",  "Amt",  "(Incl.Tax)"
    };

    private static final float ROW_H  = 14f;   // taller rows → no vertical crowding
    private static final float THDR_H = 28f;   // taller header → two lines fit with padding
    private static final float PAD    = 3f;    // horizontal cell padding

    private static final Color DARK   = new Color(20,  20,  20);
    private static final Color MUTED  = new Color(80,  80,  80);
    private static final Color LGRAY  = new Color(200, 200, 200);
    private static final Color BGALT  = new Color(248, 248, 252);
    private static final Color HDR_BG = new Color(218, 218, 218);

    public static byte[] generate(Invoice inv) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font reg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            addInvoicePage(doc, bold, reg, inv);
            addTermsPage(doc, bold, reg, inv);
            addGatePassPage(doc, bold, reg, inv);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── PAGE 1: Tax Invoice ────────────────────────────────────────────────────

    private static void addInvoicePage(PDDocument doc, PDType1Font bold, PDType1Font reg, Invoice inv)
            throws IOException {
        PDPage page = new PDPage(new PDRectangle(W, H));
        doc.addPage(page);

        String dealerName = coalesce(inv.getDealerName(), "Authorized Dealer");
        Customer cust = inv.getCustomer();
        JobCard jc    = inv.getJobCard();
        Vehicle v     = jc != null ? jc.getVehicle() : null;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = H - M;

            // ── Title bar ─────────────────────────────────────────────────────
            fill(cs, M, y - 20, CW, 20, new Color(35, 35, 35));
            textCenter(cs, bold, 12, "TAX INVOICE", W / 2f, y - 13, Color.WHITE);
            y -= 20;

            // ── Sub-header ────────────────────────────────────────────────────
            fill(cs, M, y - 12, CW, 12, new Color(235, 235, 235));
            text(cs, reg, 6.5f, "Issued under GST Invoice Rules", M + 4, y - 9, MUTED);
            textRight(cs, reg, 6.5f, "Original :For Recipient    Duplicate :For Supplier", M + CW - 4, y - 9, MUTED);
            y -= 14;

            // ── Info panel ────────────────────────────────────────────────────
            // Left 42%: customer details
            // Right 58%: invoice + vehicle + dealer details (all combined, like real invoice)
            float infoTopY = y;
            float leftW    = CW * 0.42f;
            float rightX   = M + leftW + 4;

            // Vertical divider spanning full info panel (19 rows × 9px each ≈ 180px)
            vline(cs, M + leftW + 2, infoTopY, 185, 0.4f, LGRAY);

            // LEFT: Customer details
            float ly = infoTopY;
            if (cust != null && notBlank(cust.getName())) {
                text(cs, bold, 8, cust.getName(), M, ly, DARK);                            ly -= 11;
            }
            if (cust != null && notBlank(cust.getAddress())) {
                for (String line : wordWrap(cust.getAddress(), 46)) {
                    text(cs, reg, 7, line, M, ly, DARK);                                   ly -= 9;
                }
            }
            text(cs, reg, 7, "Phone No (Res,Off,Mob): , , "
                + (cust != null ? coalesce(cust.getPhone(), "") : ""), M, ly, DARK);       ly -= 9;
            text(cs, reg, 7, "Customer GSTIN : ", M, ly, DARK);                            ly -= 9;
            text(cs, reg, 7, "A/C Code : ", M, ly, DARK);                                 ly -= 9;
            if (notBlank(inv.getPlaceOfSupply()))
                text(cs, reg, 7, "Place of Supply: " + inv.getPlaceOfSupply(), M, ly, DARK);
            ly -= 9;

            // RIGHT: Invoice + vehicle + dealer details (all fields from real invoice)
            float ry = infoTopY;
            ry = infoKv(cs, reg, rightX, ry, "Invoice No : ",            coalesce(inv.getInvoiceNumber(), "-"));
            ry = infoKv(cs, reg, rightX, ry, "Invoice Date :",           d(inv.getInvoiceDate()));
            String modelStr = v != null ? (coalesce(v.getMake(), "") + " " + coalesce(v.getModel(), "")).trim() : "";
            if (!modelStr.isBlank())
                ry = infoKv(cs, reg, rightX, ry, "Model : ",             modelStr);
            ry = infoKv(cs, reg, rightX, ry, "Chassis No : ",            coalesce(inv.getChassisNo(), ""));
            ry = infoKv(cs, reg, rightX, ry, "Insurance Co : ",          "");
            ry = infoKv(cs, reg, rightX, ry, "Insurance Type : ",        "No Insurance");
            ry = infoKv(cs, reg, rightX, ry, "Insurance Expiry Date : ", "");
            ry = infoKv(cs, reg, rightX, ry, "Kms. : ",                  inv.getKms() != null ? String.valueOf(inv.getKms()) : "");
            ry = infoKv(cs, reg, rightX, ry, "Vehicle Regn. No : ",      coalesce(inv.getVehicleRegNo(), ""));
            String jcNo = notBlank(inv.getOriginalJobCardNumber()) ? inv.getOriginalJobCardNumber()
                        : (jc != null ? jc.getJobCardNumber() : "-");
            ry = infoKv(cs, reg, rightX, ry, "Job Card No. : ",          jcNo);
            String jcDate = inv.getJobCardDate() != null ? inv.getJobCardDate().format(DATE_FMT)
                          : (jc != null && jc.getDateIn() != null ? jc.getDateIn().format(DATE_FMT) : "-");
            ry = infoKv(cs, reg, rightX, ry, "Job Card Date : ",         jcDate);
            String svc = notBlank(inv.getServiceType())
                ? inv.getServiceType().replace("_", " ")
                : (jc != null ? coalesce(jc.getServiceType(), "").replace("_", " ") : "");
            ry = infoKv(cs, reg, rightX, ry, "Service Request Type : ",  svc);
            ry = infoKv(cs, reg, rightX, ry, "Customer P.O. No - Date : ", "");
            ry = infoKv(cs, reg, rightX, ry, "Payment Method : ",        coalesce(inv.getPaymentMethod(), ""));
            ry = infoKv(cs, reg, rightX, ry, "Warranty Expired :",       "N");
            ry = infoKv(cs, reg, rightX, ry, "Dealer PAN:",              coalesce(inv.getDealerPan(), ""));
            ry = infoKv(cs, reg, rightX, ry, "Dealer GSTIN : ",          coalesce(inv.getDealerGstin(), ""));
            ry = infoKv(cs, reg, rightX, ry, "Next Service Date * : ",   "");

            y = Math.min(ly, ry) - 4;
            hline(cs, y, M, CW, 0.5f, LGRAY);
            y -= 2;

            // PAN row (customer PAN)
            text(cs, reg, 7, "PAN : ", M, y, DARK);
            y -= 4;
            hline(cs, y, M, CW, 0.5f, LGRAY);
            y -= 2;

            // ── Line items table ──────────────────────────────────────────────
            y = drawTable(cs, inv.getLineItems(), bold, reg, y);
            y -= 4;

            // ── Totals ────────────────────────────────────────────────────────
            float tx = M + CW * 0.60f;
            float tw = CW - (tx - M);

            double subTotal = inv.getLineItems() == null ? 0 :
                inv.getLineItems().stream()
                    .mapToDouble(li -> li.getBaseAmount() == null ? 0 : li.getBaseAmount())
                    .sum();
            double labourAmt  = coalesce0(inv.getLabourTaxableAmount());
            double partsNet   = coalesce0(inv.getPartsNetTaxableAmount());
            double totalTax   = coalesce0(inv.getTotalTaxAmount());
            double grandTotal = coalesce0(inv.getGrandTotal());
            double adj        = coalesce0(inv.getAdjustments());
            double gross      = grandTotal - adj;
            double finalParts = partsNet + totalTax;

            // Sub Total + Total Tax on right
            totalRow(cs, reg,  tx, tw, y, "Sub Total:",         fmt(subTotal));  y -= 12;
            totalRow(cs, reg,  tx, tw, y, "Total Tax Amount :", fmt(totalTax));  y -= 14;

            // Parts Net Taxable (left) | Final Labour Invoice Amount (right)
            text(cs, reg, 7.5f, "Parts Net Taxable Amount : " + fmt(partsNet), M, y, DARK);
            totalRow(cs, reg, tx, tw, y, "Final Labour Invoice Amount :", fmt(labourAmt));
            y -= 2;
            hline(cs, y, tx, tw, 0.4f, LGRAY);
            y -= 12;

            // Final Parts Invoice Amount (right only)
            totalRow(cs, reg,  tx, tw, y, "Final Parts Invoice Amount :", fmt(finalParts)); y -= 12;
            totalRow(cs, reg,  tx, tw, y, "Gross Amount :",               fmt(gross));       y -= 12;
            if (adj != 0)
                { totalRow(cs, reg, tx, tw, y, "Adjustments :", fmt(adj)); y -= 12; }

            hline(cs, y, tx, tw, 0.6f, DARK);
            y -= 4;

            // Amount in words (left) | Grand Total (right, bold)
            text(cs, bold, 7, toWords(grandTotal), M, y, DARK);
            totalRow(cs, bold, tx, tw, y, "Grand Total :", "Rs. " + fmt(grandTotal));
            y -= 16;

            // ── Loyalty Info ──────────────────────────────────────────────────
            hline(cs, y, M, CW, 0.4f, LGRAY);
            y -= 10;
            text(cs, bold, 7, "Loyalty Info :", M, y, DARK);
            y -= 12;
            float lw3 = CW / 3f;
            String memberNo = (cust != null && cust.getLoyaltyMemberNumber() != null)
                ? cust.getLoyaltyMemberNumber() : "Not enrolled";
            String tier     = (cust != null && cust.getLoyaltyTier() != null)
                ? cust.getLoyaltyTier() : "-";
            String pts      = (cust != null && cust.getLoyaltyPoints() != null)
                ? String.valueOf(cust.getLoyaltyPoints()) : "0";
            text(cs, reg, 7, "Member # : " + memberNo, M,           y, DARK);
            text(cs, reg, 7, "Tier # : "   + tier,     M + lw3,     y, DARK);
            text(cs, reg, 7, "Balance Points : " + pts, M + lw3 * 2, y, DARK);
            y -= 12;

            // ── Note ──────────────────────────────────────────────────────────
            hline(cs, y, M, CW, 0.3f, LGRAY);
            y -= 9;
            text(cs, bold, 7, "Note:", M, y, DARK);
            y -= 9;
            text(cs, reg, 6.5f, " 1) Insurance Liability % will only come for Insurance Job cards.", M, y, MUTED);
            y -= 11;

            // ── Loyalty promo message ─────────────────────────────────────────
            hline(cs, y, M, CW, 0.3f, LGRAY);
            y -= 8;
            int earnedPts = (int) (grandTotal / 10);
            String promoMember = memberNo.equals("Not enrolled") ? "Dear Customer" : "Dear Loyalty Member";
            String promo = promoMember + ", you have earned " + earnedPts + " points on this transaction"
                + " of Rs" + fmt(grandTotal) + ". These points have been credited to your Loyalty Account"
                + " (" + memberNo + "). Current balance: " + pts + " pts (Tier: " + tier + ")."
                + " Redeem your points at your next service visit.";
            for (String line : wordWrap(promo, 180)) {
                text(cs, reg, 6, line, M, y, MUTED);
                y -= 8;
            }
        }
    }

    // ── PAGE 2: Terms & Conditions ────────────────────────────────────────────

    private static void addTermsPage(PDDocument doc, PDType1Font bold, PDType1Font reg, Invoice inv)
            throws IOException {
        PDPage page = new PDPage(new PDRectangle(W, H));
        doc.addPage(page);

        String dealerName  = coalesce(inv.getDealerName(), "Authorized Dealer");
        String regNo       = coalesce(inv.getVehicleRegNo(), "");
        String chassisNo   = coalesce(inv.getChassisNo(), "");
        String preparedBy  = coalesce(inv.getPreparedBy(), "");
        String invoiceDate = d(inv.getInvoiceDate());

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = H - M;

            text(cs, reg, 8, "Tax Payable under Reverse Charge – No", M, y, DARK);
            y -= 18;

            text(cs, bold, 8, "Special Observations :", M, y, DARK);
            y -= 14;

            text(cs, bold, 8, "Terms  and Conditions :", M, y, DARK);
            textRight(cs, reg, 8, "E. & O. E.", M + CW, y, MUTED);
            y -= 13;

            String[] terms = {
                "1) Goods once sold will not be taken back or exchanged except as required by law.",
                "2) Only the courts of GIRIDIH shall have jurisdiction in any proceedings relating to this contract.",
                "3) I have inspected the vehicle " + regNo + " and taken delivery of the vehicle only after being"
                    + " satisfied regarding the maintenance work carried out in the vehicle. I do not",
                "   have any grievances/complaints pertaining to the chassis no " + chassisNo,
                "4) I hereby consent and authorize " + dealerName + " for usage of all the data disclosed"
                    + " above and also to share all my details and documents for promotional, marketing and"
                    + " transactional activities in accordance with the Privacy Policy. I shall inform in"
                    + " writing if I intend to withdraw my aforesaid consent."
            };
            for (String term : terms) {
                for (String line : wordWrap(term, 155)) {
                    text(cs, reg, 7.5f, line, M, y, DARK);
                    y -= 11;
                }
            }

            y -= 12;
            textRight(cs, bold, 9, "For " + dealerName, M + CW, y, DARK);
            y -= 44;

            // Signature lines
            hline(cs, y, M,              130, 0.6f, DARK);
            hline(cs, y, M + CW - 130,  130, 0.6f, DARK);
            y -= 11;
            text(cs,      reg, 8, "Customer's Signature",  M, y, DARK);
            textRight(cs, reg, 8, "Authorized signatory",  M + CW, y, DARK);
            y -= 14;

            text(cs,      reg, 8, "Prepared By : " + preparedBy, M, y, DARK);
            textRight(cs, reg, 8, "Date : " + invoiceDate,       M + CW, y, DARK);
        }
    }

    // ── PAGE 3: Gate Pass ─────────────────────────────────────────────────────

    private static void addGatePassPage(PDDocument doc, PDType1Font bold, PDType1Font reg, Invoice inv)
            throws IOException {
        PDPage page = new PDPage(new PDRectangle(W, H));
        doc.addPage(page);

        String dealerName = coalesce(inv.getDealerName(), "Authorized Dealer");
        Customer cust     = inv.getCustomer();
        JobCard jc        = inv.getJobCard();
        Vehicle v         = jc != null ? jc.getVehicle() : null;

        String invoiceRef = coalesce(inv.getInvoiceNumber(), "-")
            + " dated " + d(inv.getInvoiceDate());
        String jcNo = notBlank(inv.getOriginalJobCardNumber()) ? inv.getOriginalJobCardNumber()
                    : (jc != null ? jc.getJobCardNumber() : "-");
        String jcDateStr  = inv.getJobCardDate() != null ? inv.getJobCardDate().format(DATE_FMT)
                          : (jc != null && jc.getDateIn() != null ? jc.getDateIn().format(DATE_FMT) : "-");
        String modelStr   = v != null ? (coalesce(v.getMake(), "") + " " + coalesce(v.getModel(), "")).trim() : "";
        String custName   = cust != null ? coalesce(cust.getName(), "") : "";
        double grandTotal = coalesce0(inv.getGrandTotal());

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            // Dashed border rectangle
            cs.setStrokingColor(MUTED);
            cs.setLineWidth(0.6f);
            float[] dash = {5, 3};
            cs.setLineDashPattern(dash, 0);
            cs.addRect(M, M, CW, H - 2 * M);
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);

            float y = H - M - 8;

            // Dashed top separator line inside border
            cs.setStrokingColor(MUTED);
            cs.setLineWidth(0.4f);
            float[] dash2 = {4, 3};
            cs.setLineDashPattern(dash2, 0);
            cs.moveTo(M, y);
            cs.lineTo(M + CW, y);
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);
            y -= 18;

            textCenter(cs, bold, 11, dealerName,       W / 2f, y, DARK); y -= 14;
            textCenter(cs, bold, 10, "* GATE PASS *",  W / 2f, y, DARK); y -= 20;

            // Vertical divider in gate pass body
            vline(cs, W / 2f, y + 4, 90, 0.4f, LGRAY);

            float c1 = M + 16;
            float c2 = W / 2f + 16;

            y = gpKv(cs, bold, reg, c1, c2, y, "Customer Name :", custName,
                "Invoice Ref. :", invoiceRef);
            y = gpKv(cs, bold, reg, c1, c2, y, "Account Name :", custName,
                "Invoice Amount :", "Rs. " + fmt(grandTotal));
            y = gpKv(cs, bold, reg, c1, c2, y, "Vehicle No :", coalesce(inv.getVehicleRegNo(), ""),
                "Job Card / Order ref :", jcNo + " dated " + jcDateStr);
            y = gpKv(cs, bold, reg, c1, c2, y, "Chassis No :", coalesce(inv.getChassisNo(), ""),
                "Gate Pass No & Date :", "__________________________________");

            text(cs, bold, 8, "Model Name :", c1, y, MUTED);
            text(cs, reg,  8, modelStr,       c1 + 70, y, DARK);
            y -= 16;

            text(cs, reg, 8, "Vehicle / Goods received in good condition and to our satisfaction.", c1, y, DARK);
            y -= 34;

            // Signature line (left only)
            hline(cs, y, c1, 110, 0.5f, DARK);
            y -= 11;
            text(cs,      reg, 8, "Customer's Signature", c1, y, DARK);
            textRight(cs, bold, 9, "For " + dealerName,   M + CW - 12, y, DARK);
        }
    }

    // ── Gate pass key-value helper (two per row) ───────────────────────────────

    private static float gpKv(PDPageContentStream cs, PDType1Font bold, PDType1Font reg,
            float c1, float c2, float y, String k1, String v1, String k2, String v2) throws IOException {
        float kw1 = bold.getStringWidth(safe(k1) + " ") / 1000f * 8;
        text(cs, bold, 8, k1, c1, y, MUTED);
        text(cs, reg,  8, v1, c1 + kw1, y, DARK);
        float kw2 = bold.getStringWidth(safe(k2) + " ") / 1000f * 8;
        text(cs, bold, 8, k2, c2, y, MUTED);
        text(cs, reg,  8, v2, c2 + kw2, y, DARK);
        return y - 13;
    }

    // ── Info panel right-side key-value (all one reg-font line) ──────────────

    private static float infoKv(PDPageContentStream cs, PDType1Font reg,
            float x, float y, String key, String val) throws IOException {
        text(cs, reg, 7, key + val, x, y, DARK);
        return y - 9;
    }

    // ── Table ─────────────────────────────────────────────────────────────────

    private static float drawTable(PDPageContentStream cs,
            List<InvoiceLineItem> items, PDType1Font bold, PDType1Font reg, float y) throws IOException {

        // Header background
        fill(cs, M, y - THDR_H, CW, THDR_H, HDR_BG);

        float x = M;
        for (int i = 0; i < COL.length; i++) {
            if (i > 0) vline(cs, x, y, THDR_H, 0.4f, new Color(160, 160, 160));
            boolean leftAlign = i < 6;
            // Two header rows: top at y-8, bottom at y-18 (with 28px total header height)
            if (leftAlign) {
                textClip(cs, bold, 5.5f, HDR1[i], x + PAD, y - 8,  COL[i] - 2*PAD, DARK);
                textClip(cs, bold, 5.5f, HDR2[i], x + PAD, y - 18, COL[i] - 2*PAD, DARK);
            } else {
                textRightClip(cs, bold, 5.5f, HDR1[i], x, COL[i] - PAD, y - 8,  DARK);
                textRightClip(cs, bold, 5.5f, HDR2[i], x, COL[i] - PAD, y - 18, DARK);
            }
            x += COL[i];
        }
        vline(cs, x, y, THDR_H, 0.4f, new Color(160, 160, 160));
        y -= THDR_H;
        hline(cs, y, M, CW, 0.6f, new Color(110, 110, 110));

        if (items == null || items.isEmpty()) {
            y -= ROW_H;
            return y;
        }

        boolean alt = false;
        for (InvoiceLineItem li : items) {
            if (alt) fill(cs, M, y - ROW_H, CW, ROW_H, BGALT);
            alt = !alt;

            x = M;
            // 21 values matching COL order
            String[] vals = {
                str(li.getLineNumber()),    // 0  S No
                s(li.getHsnCode()),          // 1  HSN/SAC
                s(li.getPartNumber()),       // 2  Part#/Job Code
                s(li.getDescription()),      // 3  Particulars
                s(li.getType()),             // 4  Type
                s(li.getUom()),              // 5  UoM
                "",                          // 6  Float (N/Y)
                fq(li.getQuantity()),        // 7  Qty
                fv(li.getRate()),            // 8  Rate
                fv(li.getBaseAmount()),      // 9  Total Amt (Base)
                "",                          // 10 Extra Chg
                fv(li.getDiscountAmount()),  // 11 Disc Amt/Item
                "",                          // 12 Campaign Disc %
                "",                          // 13 Disc %
                "",                          // 14 Ins Liability %
                fv(li.getTaxableAmount()),   // 15 Taxable Amt
                fp(li.getCgstRate()),        // 16 CGST %
                fv(li.getCgstAmount()),      // 17 CGST Amt
                fp(li.getSgstRate()),        // 18 SGST %
                fv(li.getSgstAmount()),      // 19 SGST Amt
                fv(li.getTotalAmount())      // 20 Total Amt (Incl.Tax)
            };

            for (int i = 0; i < vals.length; i++) {
                float baseline = y - ROW_H + 5;   // vertical centering within taller row
                if (i < 6) textClip(cs, reg, 6.5f, vals[i], x + PAD, baseline, COL[i] - 2*PAD, DARK);
                else        textRightClip(cs, reg, 6.5f, vals[i], x, COL[i] - PAD, baseline, DARK);
                x += COL[i];
            }
            y -= ROW_H;
            hline(cs, y, M, CW, 0.3f, new Color(190, 190, 190));
        }
        hline(cs, y, M, CW, 0.6f, new Color(80, 80, 80));
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
        float maxW = colW - PAD;   // clip + right-padding so text never touches border
        while (s.length() > 1 && f.getStringWidth(s) / 1000f * sz > maxW)
            s = s.substring(0, s.length() - 1);
        textRight(cs, f, sz, s, colX + colW - PAD, y, c);
    }

    private static void fill(PDPageContentStream cs, float x, float y, float w, float h, Color c)
            throws IOException {
        cs.setNonStrokingColor(c);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private static void hline(PDPageContentStream cs, float y, float x, float w, float lw, Color c)
            throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(lw);
        cs.moveTo(x, y);
        cs.lineTo(x + w, y);
        cs.stroke();
    }

    private static void vline(PDPageContentStream cs, float x, float yTop, float h, float lw, Color c)
            throws IOException {
        cs.setStrokingColor(c);
        cs.setLineWidth(lw);
        cs.moveTo(x, yTop);
        cs.lineTo(x, yTop - h);
        cs.stroke();
    }

    private static void totalRow(PDPageContentStream cs, PDType1Font f,
            float x, float w, float y, String label, String val) throws IOException {
        text(cs, f, 7.5f, label, x + 4, y, DARK);
        textRight(cs, f, 7.5f, val, x + w - 4, y, DARK);
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private static String fmt(double d) {
        return String.format(Locale.US, "%,.2f", d);
    }

    private static String fv(Double d) {
        if (d == null || d == 0.0) return "";
        return String.format(Locale.US, "%,.2f", d);
    }

    private static String fp(Double d) {
        if (d == null || d == 0.0) return "";
        return String.format(Locale.US, "%.0f", d);
    }

    private static String fq(Double d) {
        if (d == null) return "";
        if (d == Math.floor(d)) return String.valueOf(d.intValue());
        return String.format(Locale.US, "%.2f", d);
    }

    private static String d(java.time.LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "-";
    }

    private static double coalesce0(Double d)       { return d == null ? 0 : d; }
    private static String  str(Integer i)            { return i == null ? "" : String.valueOf(i); }
    private static String  s(String v)               { return v == null ? "" : v; }
    private static String  coalesce(String a, String b) { return (a != null && !a.isBlank()) ? a : b; }
    private static boolean notBlank(String v)        { return v != null && !v.isBlank(); }

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
                if (!sb.isEmpty()) lines.add(sb.toString().trim());
                sb = new StringBuilder(word);
            } else {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(word);
            }
        }
        if (!sb.isEmpty()) lines.add(sb.toString().trim());
        return lines.toArray(new String[0]);
    }

    // ── Number to words (Indian numbering) ────────────────────────────────────

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private static String toWords(double amount) {
        long rupees = (long) Math.floor(amount);
        int  paise  = (int)  Math.round((amount - rupees) * 100);
        String result = "Rupees " + convert(rupees);
        if (paise > 0) result += " and " + convert(paise) + " Paise";
        return result + " Only.";
    }

    private static String convert(long n) {
        if (n == 0) return "Zero";
        if (n < 0)  return "Minus " + convert(-n);
        StringBuilder sb = new StringBuilder();
        if (n >= 10_000_000) { sb.append(convert(n / 10_000_000)).append(" Crore ");  n %= 10_000_000; }
        if (n >=    100_000) { sb.append(convert(n /    100_000)).append(" Lakh ");   n %=    100_000; }
        if (n >=      1_000) { sb.append(convert(n /      1_000)).append(" Thousand "); n %=    1_000; }
        if (n >=        100) { sb.append(ONES[(int)(n / 100)]).append(" Hundred ");   n %=        100; }
        if (n >= 20) {
            sb.append(TENS[(int)(n / 10)]);
            if (n % 10 > 0) sb.append(" ").append(ONES[(int)(n % 10)]);
        } else if (n > 0) {
            sb.append(ONES[(int) n]);
        }
        return sb.toString().trim();
    }
}
