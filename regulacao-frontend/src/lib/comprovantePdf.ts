import { jsPDF } from "jspdf";
import { env } from "$env/dynamic/public";
import { base } from "$app/paths";

export const brasaoUrl = `${base}/images/${env.PUBLIC_MUNICIPIO_BRASAO ?? "brasao_conceicao.png"}`;

export async function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = (e) =>
      reject(new Error(`Falha ao carregar imagem: ${url}. Erro: ${e}`));
    img.crossOrigin = "Anonymous";
    img.src = url;
  });
}

export async function gerarComprovantePDF(dadosPDF: {
  solicitacaoId: number;
  nomePaciente: string;
  cpfPaciente: string;
  usfOrigem?: string | null;
  unidadeNome?: string | null;
  cns?: string;
  examesNomes: string[];
  dataAgendada: string;
  turno: "MANHA" | "TARDE";
  localLabel: string;
  observacoes: string;
  profissionalNome?: string | null;
  agendadoPorNome?: string | null;
  horarioInfo?: string | null;
}) {
  const doc = new jsPDF({ unit: "pt", format: "a4", orientation: "portrait" });
  const margin = { top: 80, left: 40, right: 40, bottom: 60 };
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  let currentY = margin.top;

  const corDestaque = "#0D7244";
  const corPadrao = "#1f2937";
  const corLabel = "#374151";

  const imgWidth = 60;
  const imgHeight = 60;
  const imgX = pageWidth / 2 - imgWidth / 2;
  const imgY = margin.top - 70;

  try {
    const brasaoImage = await loadImage(brasaoUrl);
    doc.addImage(brasaoImage, "PNG", imgX, imgY, imgWidth, imgHeight);
  } catch (error) {
    console.error("Erro ao adicionar brasão ao PDF:", error);
  }

  currentY = imgY + imgHeight + 25;

  doc.setFontSize(20);
  doc.setTextColor("#115e59");
  doc.setFont("helvetica", "bold");
  doc.text("Central de Regulação", pageWidth / 2, currentY, {
    align: "center",
  });
  currentY += 24;
  doc.setFontSize(14);
  doc.setTextColor("#333333");
  doc.setFont("helvetica", "normal");
  doc.text(env.PUBLIC_MUNICIPIO_NOME ?? "", pageWidth / 2, currentY, {
    align: "center",
  });
  currentY += 40;
  doc.setFontSize(18);
  doc.setFont("helvetica", "bold");
  doc.text("Comprovante de Agendamento", pageWidth / 2, currentY, {
    align: "center",
  });
  currentY += 35;

  const allInfo = [
    {
      label: "ID Solicitação",
      value: String(dadosPDF.solicitacaoId),
      color: corDestaque,
      style: "bold",
    },
    {
      label: "Paciente",
      value: dadosPDF.nomePaciente,
      color: corPadrao,
      style: "bold",
    },
    { label: "CPF", value: dadosPDF.cpfPaciente, color: corPadrao },
    {
      label: "Unidade",
      value: dadosPDF.unidadeNome || dadosPDF.usfOrigem || "Não informado",
      color: corPadrao,
    },
    ...(dadosPDF.cns
      ? [{ label: "CNS", value: dadosPDF.cns, color: corPadrao }]
      : []),
    {
      label: "Exames Agendados",
      value: dadosPDF.examesNomes.join(", "),
      color: corDestaque,
      style: "bold",
    },
    {
      label: "Data Agendada",
      value: new Date(dadosPDF.dataAgendada + "T00:00:00").toLocaleDateString(
        "pt-BR",
      ),
      color: corDestaque,
      style: "bold",
    },
    {
      label: "Turno",
      value: dadosPDF.turno === "MANHA" ? "Manhã" : "Tarde",
      color: corPadrao,
    },
    {
      label: "Local",
      value: dadosPDF.localLabel,
      color: corDestaque,
      style: "bold",
    },
    ...(dadosPDF.profissionalNome
      ? [
          {
            label: "Profissional",
            value: dadosPDF.profissionalNome,
            color: corPadrao,
          },
        ]
      : []),
    ...(dadosPDF.horarioInfo
      ? [{ label: "Horário", value: dadosPDF.horarioInfo, color: corPadrao }]
      : []),
    {
      label: "Observações",
      value: dadosPDF.observacoes || "Nenhuma",
      color: corPadrao,
    },
    ...(dadosPDF.agendadoPorNome
      ? [
          {
            label: "Agendado por",
            value: dadosPDF.agendadoPorNome,
            color: "#6b7280",
          },
        ]
      : []),
  ];

  allInfo.forEach((info, index) => {
    const bgColor = index % 2 === 0 ? "#f3f4f6" : "#ffffff";
    const lines = doc.splitTextToSize(
      String(info.value),
      pageWidth - margin.left - margin.right - 130,
    );
    const rowHeight = Math.max(28, lines.length * 14 + 14);

    doc.setFillColor(bgColor);
    doc.rect(
      margin.left,
      currentY - 14,
      pageWidth - margin.left - margin.right,
      rowHeight,
      "F",
    );

    doc.setFontSize(11);
    doc.setFont("helvetica", "bold");
    doc.setTextColor(corLabel);
    doc.text(info.label, margin.left + 10, currentY);

    const style = info.style || "normal";
    const color = info.color || corPadrao;

    doc.setFont("helvetica", style as any);
    doc.setTextColor(color);
    doc.text(lines, margin.left + 130, currentY);

    currentY += rowHeight;
  });

  currentY += 20;
  doc.setFontSize(11);
  doc.setTextColor("#dc3545");
  doc.setFont("helvetica", "bold");
  doc.text("Orientação Importante:", margin.left, currentY);
  currentY += 18;
  doc.setFont("helvetica", "normal");
  doc.setTextColor("#212529");
  doc.text(
    "• Comparecer com 15 minutos de antecedência.",
    margin.left + 10,
    currentY,
  );
  currentY += 16;
  doc.text(
    "• Levar RG, CPF, Cartão do SUS e este comprovante.",
    margin.left + 10,
    currentY,
  );
  currentY += 16;
  doc.text(
    "• Em caso de não comparecimento, favor informar com antecedência.",
    margin.left + 10,
    currentY,
  );

  const hoje = new Date().toLocaleDateString("pt-BR");
  const hora = new Date().toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });
  doc.setFontSize(9);
  doc.setTextColor("#6c757d");
  doc.text(
    `Emitido em: ${hoje} às ${hora}`,
    margin.left,
    pageHeight - margin.bottom + 20,
  );
  doc.text(
    `SIRGE System v1.0`,
    pageWidth - margin.right,
    pageHeight - margin.bottom + 20,
    { align: "right" },
  );

  doc.save(
    `comprovante_${dadosPDF.nomePaciente.replace(/\s+/g, "_")}_${dadosPDF.solicitacaoId}.pdf`,
  );
}
