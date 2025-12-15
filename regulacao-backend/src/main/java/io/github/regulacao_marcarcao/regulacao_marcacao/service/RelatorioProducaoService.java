package io.github.regulacao_marcarcao.regulacao_marcacao.service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.relatorio.producao.RelatorioProducaoViewDTO;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendamentoSolicitacao;
import io.github.regulacao_marcarcao.regulacao_marcacao.repository.AgendamentoSolicitacaoRepository;

@Service
public class RelatorioProducaoService {
    

    private final AgendamentoSolicitacaoRepository agendamentoSolicitacaoRepository;


    public RelatorioProducaoService(AgendamentoSolicitacaoRepository agendamentoSolicitacaoRepository) {
        this.agendamentoSolicitacaoRepository = agendamentoSolicitacaoRepository;
    }

    public List<RelatorioProducaoViewDTO> dadosDoRelatorio(){

        LocalDate hoje = LocalDate.now();
        LocalDateTime inicio  = hoje.atStartOfDay();
        LocalDateTime fim = inicio.plusDays(1).minusNanos(1);
        List<AgendamentoSolicitacao> agendamentos = agendamentoSolicitacaoRepository.findByDataCriacaoBetween(inicio, fim);
        List<RelatorioProducaoViewDTO> relatorioProducaoViewDTOs = agendamentos.stream().map(RelatorioProducaoViewDTO::fromEntity).toList();
        return relatorioProducaoViewDTOs;
        
    }


    public byte[] gerarExcel(List<RelatorioProducaoViewDTO> dados) throws Exception{
        

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Agendamentos do Dia");

       for(int i=0; i< 7; i++){
        sheet.setColumnWidth(i, 20*256);
       }

       Row rowLogo = sheet.createRow(0);
       rowLogo.setHeightInPoints(60);
       Row rowTitle = sheet.createRow(1);
       rowTitle.setHeightInPoints(25);

        try(InputStream logoStream = new ClassPathResource("images/brasao.png").getInputStream()){
            if (logoStream != null) {
                byte[] bytes = logoStream.readAllBytes();

                int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);

                CreationHelper helper = workbook.getCreationHelper();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = helper.createClientAnchor();

                anchor.setCol1(0);
                anchor.setRow1(0);

                
                Picture picture = drawing.createPicture(anchor, pictureIdx);
            }
            else{
                System.out.println("Logo não encontrada no caminho");
            }
        }
    

        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);

        CellStyle tittStyle = workbook.createCellStyle();
        tittStyle.setFont(titleFont);
        tittStyle.setAlignment(HorizontalAlignment.CENTER);
        tittStyle.setVerticalAlignment(VerticalAlignment.CENTER);
      
      
        sheet.addMergedRegion(new CellRangeAddress(0,1,1,6));


      
        
        
        
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        
        Cell cellTitle =  rowLogo.createCell(1);
        cellTitle.setCellValue("Sistema Integrado de Regulação Gerencial - SIRG");
        cellTitle.setCellStyle(tittStyle);
        
        int headerRowIndex = 3;
        
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Row header = sheet.createRow(headerRowIndex++);
        
        String[] titulos = {
            "Paciente", 
            "CPF", 
            "USF", 
            "Data Agendada", 
            "Turno", 
            "Local Agendado", 
            "Especialidades"};


        

        for(int i = 0 ; i< titulos.length; i++){
            Cell cell = header.createCell(i);
            cell.setCellValue(titulos[i]);
            cell.setCellStyle(headerStyle);
        }

    


        int rowNum = headerRowIndex;
        for(RelatorioProducaoViewDTO item : dados){
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            
            String especialidadesStr = item.solicitacaoSimpleViewDTO().especialidades().stream().collect(Collectors.joining(","));
            row.createCell(6).setCellValue(especialidadesStr);

            String[] items = {
                item.solicitacaoSimpleViewDTO().nomePaciente(),
                item.solicitacaoSimpleViewDTO().cpfPaciente(), 
                item.solicitacaoSimpleViewDTO().usfOrigem().toString(), 
                item.agendamentoSolicitacaoSimpleViewDTO().dataAgendada(),
                item.agendamentoSolicitacaoSimpleViewDTO().turno(),
                item.agendamentoSolicitacaoSimpleViewDTO().localAgendado(),
                especialidadesStr
            };

            int totalDeItens = dados.size();

            for(int i=0; i< items.length; i++){
                Cell cell = row.createCell(i);
                cell.setCellValue(items[i]);
            }

            for(int i=0; i< items.length; i++){
                sheet.autoSizeColumn(i);
            }

            Row totalRow = sheet.createRow(rowNum + 1);

            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("Total de Agendamentos");

            Cell totalCell = totalRow.createCell(1);
            totalCell.setCellValue(totalDeItens);
            
            
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
    
        return out.toByteArray();
    }


 
}
