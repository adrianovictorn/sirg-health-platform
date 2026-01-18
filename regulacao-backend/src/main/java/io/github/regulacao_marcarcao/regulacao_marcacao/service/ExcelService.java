package io.github.regulacao_marcarcao.regulacao_marcacao.service;


import io.github.regulacao_marcarcao.regulacao_marcacao.repository.SolicitacaoEspecialidadeRepository;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoAgendadoProjection;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection.RelatorioGrupoPendenteProjection;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ExcelService {

    private final SolicitacaoEspecialidadeRepository especialidadeRepository;

    public ByteArrayInputStream gerarPlanilhaAguardando(String grupo) throws IOException {
        if (grupo == null || grupo.isEmpty()) {
            return new ByteArrayInputStream(new ByteArrayOutputStream().toByteArray());
        }

        List<RelatorioGrupoPendenteProjection> rows = especialidadeRepository.listarPendentesPorGrupo(grupo);


         if (rows == null || rows.isEmpty()) {
            return new ByteArrayInputStream(new ByteArrayOutputStream().toByteArray());
        }
        
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Relatório de Pendências");

        // --- ESTILOS ---
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
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle.setAlignment(HorizontalAlignment.LEFT);
        dataStyle.setWrapText(true);

        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.cloneStyleFrom(dataStyle);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] headers = {"NOME", "CPF", "CNS", "NASCIMENTO", "USF", "ESPECIALIDADE/EXAME", "DATA MALOTE", "PRIORIDADE"};
        int numberOfColumns = headers.length;

        // --- CABEÇALHO COM BRASÃO CENTRALIZADO ---
        try (InputStream is = new ClassPathResource("images/brasao.png").getInputStream()) {
            byte[] bytes = IOUtils.toByteArray(is);
            int picIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = workbook.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            
            anchor.setCol1(4);
            anchor.setRow1(0);
            anchor.setCol2(5);
            anchor.setRow2(3);
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            Picture pic = drawing.createPicture(anchor, picIdx);
            pic.resize(0.99);

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
        cellSub.setCellValue("Central de Regulação de Conceição do Almeida");
        cellSub.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, numberOfColumns - 1));


        Row rowHead = sheet.createRow(6);
        rowHead.setHeightInPoints(18);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = rowHead.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        int r = 7;
        
        for (RelatorioGrupoPendenteProjection it: rows) {
            Row row = sheet.createRow(r++);
            
            float alturaBase = 18f;
            int caracteresPorLinha = 45;
            int linhas = Math.max(1, (int) Math.ceil((double) safe(it.getEspecialidades()).length() / caracteresPorLinha));
            row.setHeightInPoints(Math.max(alturaBase, linhas * alturaBase));

            Cell c0 = row.createCell(0); c0.setCellValue(it.getNomePaciente()); c0.setCellStyle(dataStyle);
            Cell c1 = row.createCell(1); c1.setCellValue(it.getCpfPaciente()); c1.setCellStyle(centerStyle);
            Cell c2 = row.createCell(2); c2.setCellValue(it.getCns()); c2.setCellStyle(centerStyle);
            Cell c3 = row.createCell(3); c3.setCellValue(it.getDataNascimento() != null ? it.getDataNascimento().format(fmt) : ""); c3.setCellStyle(centerStyle);
            Cell c4 = row.createCell(4); c4.setCellValue(safe(it.getUsfOrigem())); c4.setCellStyle(centerStyle);

            
            
            Cell c5 = row.createCell(5); c5.setCellValue(it.getEspecialidades()); c5.setCellStyle(dataStyle);
            
            Cell c6 = row.createCell(6);
            c6.setCellValue(it.getDataMalote() != null ? it.getDataMalote().format(fmt) : "");
            c6.setCellStyle(centerStyle);

            

            Cell c7 = row.createCell(7);
            c7.setCellValue(it.getPrioridade());
            c7.setCellStyle(centerStyle);
        }

        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 15 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 15 * 256);
        sheet.setColumnWidth(4, 15 * 256);
        sheet.setColumnWidth(5, 45 * 256);
        sheet.setColumnWidth(6, 15 * 256);
        sheet.setColumnWidth(7, 15 * 256);

        Footer footer = sheet.getFooter();
        footer.setCenter("Direitos reservados a SIRG, desenvolvido por Adriano Victor N. Ribeiro e Filipe da Silva Ribeiro");

        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    public boolean haDadosParaRelatorioAguardando(String grupo) {
         if (grupo == null || grupo.isBlank()) return false;
        return especialidadeRepository.countPendentesPorGrupo(grupo) > 0;        
    }

    public ByteArrayInputStream gerarPlanilhaAgendamentos(String grupo, LocalDate data) throws IOException {
        if (grupo == null || grupo.isEmpty()) {
            return new ByteArrayInputStream(new ByteArrayOutputStream().toByteArray());
        }
        List<RelatorioGrupoAgendadoProjection> rows = especialidadeRepository.listarAgendadosPorGrupoEData(grupo, data);
        
       if (rows == null || rows.isEmpty()) {
        return new ByteArrayInputStream(new ByteArrayOutputStream().toByteArray());
       }

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Relatório de Agendamentos");

        // --- ESTILOS ---
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

        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle.setAlignment(HorizontalAlignment.LEFT);
        dataStyle.setWrapText(true);

        CellStyle centerStyle = workbook.createCellStyle();
        centerStyle.cloneStyleFrom(dataStyle);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] headers = {"NOME", "CPF", "CNS", "NASCIMENTO", "USF", "ESPECIALIDADE/EXAME", "AGENDAMENTO", "TURNO"};
        int numberOfColumns = headers.length;


        // --- CABEÇALHO COM BRASÃO CENTRALIZADO ---
        try (InputStream is = new ClassPathResource("images/brasao.png").getInputStream()) {
            byte[] bytes = IOUtils.toByteArray(is);
            int picIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
            CreationHelper helper = workbook.getCreationHelper();
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();

            anchor.setCol1(4);
            anchor.setRow1(0);
            anchor.setCol2(5);
            anchor.setRow2(3);
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            Picture pic = drawing.createPicture(anchor, picIdx);
            pic.resize(0.99);

        

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
        cellSub.setCellValue("Central de Regulação de Conceição do Almeida");
        cellSub.setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, numberOfColumns - 1));

        Row rowHead = sheet.createRow(6);
        rowHead.setHeightInPoints(18);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = rowHead.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        int r = 7;
        
        for (RelatorioGrupoAgendadoProjection it : rows) {
            Row row = sheet.createRow(r++);
            

            float alturaBase = 18f;
            int caracteresPorLinha = 45;
            int linhas = Math.max(1, (int) Math.ceil((double) safe(it.getEspecialidades()).length() / caracteresPorLinha));
            row.setHeightInPoints(Math.max(alturaBase, linhas * alturaBase));

            Cell c0 = row.createCell(0); c0.setCellValue(it.getNomePaciente()); c0.setCellStyle(dataStyle);
            Cell c1 = row.createCell(1); c1.setCellValue(it.getCpfPaciente()); c1.setCellStyle(centerStyle);
            Cell c2 = row.createCell(2); c2.setCellValue(it.getCns()); c2.setCellStyle(centerStyle);
            Cell c3 = row.createCell(3); c3.setCellValue(it.getDataNascimento() != null ? it.getDataNascimento().format(fmt) : ""); c3.setCellStyle(centerStyle);
            Cell c4 = row.createCell(4); c4.setCellValue(safe(it.getUsfOrigem())); c4.setCellStyle(centerStyle);

            Cell c5 = row.createCell(5); c5.setCellValue(it.getEspecialidades()); c5.setCellStyle(dataStyle);

            
            Cell c6 = row.createCell(6);
            c6.setCellValue(it.getDataAgendada() != null ? it.getDataAgendada().format(fmt) : "");
            c6.setCellStyle(centerStyle);

            Cell c7 = row.createCell(7);
            c7.setCellValue(safe(it.getTurno()));
            c7.setCellStyle(centerStyle);
        }

        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 15 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 15 * 256);
        sheet.setColumnWidth(4, 15 * 256);
        sheet.setColumnWidth(5, 45 * 256);
        sheet.setColumnWidth(6, 15 * 256);
        sheet.setColumnWidth(7, 12 * 256);

        Footer footer = sheet.getFooter();
        footer.setCenter("Direitos reservados a SIRG, desenvolvido por Adriano Victor N. Ribeiro e Filipe da Silva Ribeiro");

        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    public boolean haDadosParaRelatorio(String grupo, LocalDate data) {
        if (grupo == null || grupo.isBlank() || data == null) return false;
        return especialidadeRepository.countAgendadosPorGrupoEData(grupo, data) > 0;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

}
