package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.config.InstanceContext;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalQuantitativoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.ProfissionalSolicitacaoDetalheProjection;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RelatorioProfissionalExcelService {

    private final InstanceContext instanceContext;

    private String getNomeCentral() {
        try {
            return "Central de Regulação de " + instanceContext.getMunicipioLocal().getNome();
        } catch (Exception e) {
            return "Central de Regulação de " + instanceContext.getNomeIdentificador();
        }
    }

    private String getBrasaoPath() {
        String id = Normalizer.normalize(instanceContext.getNomeIdentificador(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        String specific = "images/brasao_" + id + ".png";
        try (InputStream test = new ClassPathResource(specific).getInputStream()) {
            return specific;
        } catch (Exception e) {
            return "images/brasao.png";
        }
    }

    private static String tipoLabel(String categoria) {
        if ("ESPECIALIDADE_MEDICA".equals(categoria)) return "Consulta";
        if ("EXAME_OU_PROCEDIMENTO".equals(categoria)) return "Exame/Procedimento";
        return safe(categoria);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void desenharCabecalho(Workbook workbook, Sheet sheet, String[] headers, String subtitulo) {
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleFont.setFontName("Arial");
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        Font subtitleFont = workbook.createFont();
        subtitleFont.setFontHeightInPoints((short) 10);
        subtitleFont.setFontName("Arial");
        CellStyle subtitleStyle = workbook.createCellStyle();
        subtitleStyle.setFont(subtitleFont);
        subtitleStyle.setAlignment(HorizontalAlignment.CENTER);
        subtitleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 10);
        headerFont.setFontName("Arial");
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        int numberOfColumns = headers.length;

        try (InputStream is = new ClassPathResource(getBrasaoPath()).getInputStream()) {
            byte[] bytes = IOUtils.toByteArray(is);
            int picIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = workbook.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(Math.max(0, numberOfColumns / 2 - 1));
            anchor.setRow1(0);
            anchor.setCol2(Math.max(1, numberOfColumns / 2));
            anchor.setRow2(3);
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            drawing.createPicture(anchor, picIdx).resize(0.99);
        } catch (Exception e) {
            System.err.println("Erro ao carregar brasão: " + e.getMessage());
        }

        Row rowTitle = sheet.createRow(3);
        rowTitle.setHeightInPoints(20);
        Cell cellTitle = rowTitle.createCell(0);
        cellTitle.setCellValue("SIRG - Sistema de Regulação");
        cellTitle.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, numberOfColumns - 1));

        Row rowSub = sheet.createRow(4);
        rowSub.setHeightInPoints(16);
        Cell cellSub = rowSub.createCell(0);
        cellSub.setCellValue(getNomeCentral() + " — " + subtitulo);
        cellSub.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, numberOfColumns - 1));

        Row rowDate = sheet.createRow(5);
        rowDate.setHeightInPoints(14);
        Cell cellDate = rowDate.createCell(0);
        cellDate.setCellValue("Gerado em: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        cellDate.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(5, 5, 0, numberOfColumns - 1));

        Row rowHead = sheet.createRow(7);
        rowHead.setHeightInPoints(18);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = rowHead.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle dataStyle(Workbook workbook) {
        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle.setAlignment(HorizontalAlignment.LEFT);
        dataStyle.setWrapText(true);
        return dataStyle;
    }

    private CellStyle centerStyle(Workbook workbook, CellStyle dataStyle) {
        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.cloneStyleFrom(dataStyle);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);
        return centerStyle;
    }

    private void aplicarRodape(Sheet sheet) {
        sheet.getFooter().setCenter("Direitos reservados a SIRG, desenvolvido por Adriano Victor N. Ribeiro e Filipe da Silva Ribeiro");
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }

    public byte[] gerarExcelDetalhado(List<ProfissionalSolicitacaoDetalheProjection> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Solicitações por Profissional");

            String[] headers = {"PROFISSIONAL", "CONSELHO/REGISTRO", "TIPO", "EXAME/PROCEDIMENTO/CONSULTA", "PACIENTE", "CPF", "DATA DA SOLICITAÇÃO"};
            desenharCabecalho(workbook, sheet, headers, "Solicitações por Profissional");

            CellStyle dataStyle = dataStyle(workbook);
            CellStyle centerStyle = centerStyle(workbook, dataStyle);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            int r = 8;
            for (ProfissionalSolicitacaoDetalheProjection it : rows) {
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(18f);

                String registro = String.join(" ", safe(it.getConselho()), safe(it.getNumeroRegistro())).trim();

                Cell c0 = row.createCell(0); c0.setCellValue(safe(it.getProfissionalNome())); c0.setCellStyle(dataStyle);
                Cell c1 = row.createCell(1); c1.setCellValue(registro); c1.setCellStyle(centerStyle);
                Cell c2 = row.createCell(2); c2.setCellValue(tipoLabel(it.getTipo())); c2.setCellStyle(centerStyle);
                Cell c3 = row.createCell(3); c3.setCellValue(safe(it.getEspecialidadeNome())); c3.setCellStyle(dataStyle);
                Cell c4 = row.createCell(4); c4.setCellValue(safe(it.getPacienteNome())); c4.setCellStyle(dataStyle);
                Cell c5 = row.createCell(5); c5.setCellValue(safe(it.getCpfPaciente())); c5.setCellStyle(centerStyle);
                Cell c6 = row.createCell(6);
                c6.setCellValue(it.getDataSolicitacao() != null ? it.getDataSolicitacao().format(fmt) : "");
                c6.setCellStyle(centerStyle);
            }

            sheet.setColumnWidth(0, 35 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 20 * 256);
            sheet.setColumnWidth(3, 35 * 256);
            sheet.setColumnWidth(4, 35 * 256);
            sheet.setColumnWidth(5, 15 * 256);
            sheet.setColumnWidth(6, 18 * 256);

            aplicarRodape(sheet);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] gerarExcelQuantitativo(List<ProfissionalQuantitativoProjection> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Quantitativo por Profissional");

            String[] headers = {"PROFISSIONAL", "CONSELHO/REGISTRO", "TOTAL NO PERÍODO", "CONSULTAS", "EXAMES/PROCEDIMENTOS"};
            desenharCabecalho(workbook, sheet, headers, "Quantitativo de Solicitações por Profissional");

            CellStyle dataStyle = dataStyle(workbook);
            CellStyle centerStyle = centerStyle(workbook, dataStyle);

            int r = 8;
            for (ProfissionalQuantitativoProjection it : rows) {
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(18f);

                String registro = String.join(" ", safe(it.getConselho()), safe(it.getNumeroRegistro())).trim();

                Cell c0 = row.createCell(0); c0.setCellValue(safe(it.getProfissionalNome())); c0.setCellStyle(dataStyle);
                Cell c1 = row.createCell(1); c1.setCellValue(registro); c1.setCellStyle(centerStyle);
                Cell c2 = row.createCell(2); c2.setCellValue(it.getTotalNoPeriodo()); c2.setCellStyle(centerStyle);
                Cell c3 = row.createCell(3); c3.setCellValue(it.getConsultas()); c3.setCellStyle(centerStyle);
                Cell c4 = row.createCell(4); c4.setCellValue(it.getExamesProcedimentos()); c4.setCellStyle(centerStyle);
            }

            sheet.setColumnWidth(0, 40 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 18 * 256);
            sheet.setColumnWidth(3, 15 * 256);
            sheet.setColumnWidth(4, 22 * 256);

            aplicarRodape(sheet);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
