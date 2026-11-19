package com.wirdi.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** Writes selected course notes to a printer-friendly PDF without external storage permissions. */
final class CourseNotesPdfExporter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 46;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final float CONTENT_BOTTOM = PAGE_HEIGHT - 46f;

    private CourseNotesPdfExporter() { }

    static void write(OutputStream output, String courseName, String sectionTitle,
                      String untitledNoteTitle, List<CourseNote> notes) throws IOException {
        PdfDocument document = new PdfDocument();
        try {
            PageWriter writer = new PageWriter(document, courseName, sectionTitle);
            for (CourseNote note : notes) {
                String titleText = note.title == null || note.title.trim().isEmpty()
                        ? untitledNoteTitle : note.title.trim();
                StaticLayout title = makeLayout(titleText, 16f, true, Color.rgb(31, 68, 109));
                StaticLayout body = makeLayout(note.text == null ? "" : note.text, 13.5f, false,
                        Color.rgb(35, 42, 50));
                writer.keepNoteTogether(title.getHeight() + 7f + Math.max(14, body.getLineBottom(0)));
                writer.drawLayout(title);
                writer.addGap(6f);
                writer.drawLayout(body);
                writer.addGap(20f);
            }
            writer.finishPage();
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    private static StaticLayout makeLayout(String text, float size, boolean bold, int color) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setIncludePad(true)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                .build();
    }

    private static final class PageWriter {
        private final PdfDocument document;
        private final String courseName;
        private final String sectionTitle;
        private PdfDocument.Page page;
        private Canvas canvas;
        private float contentTop;
        private float y;
        private int pageNumber;

        PageWriter(PdfDocument document, String courseName, String sectionTitle) {
            this.document = document;
            this.courseName = courseName == null ? "" : courseName;
            this.sectionTitle = sectionTitle == null ? "" : sectionTitle;
            startPage();
        }

        void keepNoteTogether(float minimumHeight) {
            if (y > contentTop && y + minimumHeight > CONTENT_BOTTOM) startPage();
        }

        void addGap(float gap) {
            y += gap;
            if (y >= CONTENT_BOTTOM) startPage();
        }

        void drawLayout(StaticLayout layout) {
            int firstLine = 0;
            while (firstLine < layout.getLineCount()) {
                if (y >= CONTENT_BOTTOM) startPage();
                float available = CONTENT_BOTTOM - y;
                int lastLine = firstLine;
                while (lastLine < layout.getLineCount()
                        && layout.getLineBottom(lastLine) - layout.getLineTop(firstLine) <= available) {
                    lastLine++;
                }
                if (lastLine == firstLine) {
                    if (y > contentTop) {
                        startPage();
                        continue;
                    }
                    lastLine = firstLine + 1;
                }
                int sourceTop = layout.getLineTop(firstLine);
                int sourceBottom = layout.getLineBottom(lastLine - 1);
                canvas.save();
                canvas.clipRect(MARGIN, y, PAGE_WIDTH - MARGIN, CONTENT_BOTTOM);
                canvas.translate(MARGIN, y - sourceTop);
                layout.draw(canvas);
                canvas.restore();
                y += sourceBottom - sourceTop;
                firstLine = lastLine;
                if (firstLine < layout.getLineCount()) startPage();
            }
        }

        void finishPage() {
            if (page != null) {
                document.finishPage(page);
                page = null;
                canvas = null;
            }
        }

        private void startPage() {
            finishPage();
            pageNumber++;
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
            page = document.startPage(info);
            canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);

            TextPaint sectionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            sectionPaint.setColor(Color.rgb(93, 105, 118));
            sectionPaint.setTextSize(10.5f);
            StaticLayout section = StaticLayout.Builder.obtain(sectionTitle, 0, sectionTitle.length(),
                            sectionPaint, CONTENT_WIDTH)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                    .setIncludePad(true)
                    .build();
            canvas.save();
            canvas.translate(MARGIN, 24f);
            section.draw(canvas);
            canvas.restore();

            TextPaint coursePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            coursePaint.setColor(Color.rgb(23, 43, 65));
            coursePaint.setTextSize(17f);
            coursePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            StaticLayout heading = StaticLayout.Builder.obtain(courseName, 0, courseName.length(),
                            coursePaint, CONTENT_WIDTH)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                    .setIncludePad(true)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                    .build();
            canvas.save();
            canvas.translate(MARGIN, 42f);
            heading.draw(canvas);
            canvas.restore();

            float dividerY = Math.max(69f, 42f + heading.getHeight() + 8f);
            Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
            divider.setColor(Color.rgb(205, 214, 222));
            divider.setStrokeWidth(1f);
            canvas.drawLine(MARGIN, dividerY, PAGE_WIDTH - MARGIN, dividerY, divider);
            contentTop = dividerY + 16f;
            y = contentTop;
        }
    }
}
