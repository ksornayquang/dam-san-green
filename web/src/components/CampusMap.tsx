import { useEffect, useMemo, useRef, useState } from "react";
import type { TrashReport } from "../types";

type CampusMapProps = {
  mode: "gps" | "3d";
  reports: TrashReport[];
  zoom: number;
};

type Block = {
  label: string;
  x: number;
  y: number;
  width: number;
  height: number;
  elevation: number;
  color: string;
  roof?: string;
};

const SCHOOL = { lat: 12.900868056693273, lon: 108.2911159047231 };

const blocks: Block[] = [
  { label: "Nhà ăn", x: .18, y: .13, width: .17, height: .09, elevation: .06, color: "#eadfc9", roof: "#a9433c" },
  { label: "NT HS 1", x: .42, y: .13, width: .07, height: .28, elevation: .12, color: "#e5ece9", roof: "#973a38" },
  { label: "NT HS 2", x: .60, y: .16, width: .07, height: .27, elevation: .12, color: "#e5ece9", roof: "#973a38" },
  { label: "11A1", x: .08, y: .28, width: .19, height: .17, elevation: .012, color: "#4eb777" },
  { label: "10A3", x: .31, y: .29, width: .20, height: .17, elevation: .012, color: "#e6cd4b" },
  { label: "11A5", x: .72, y: .16, width: .17, height: .20, elevation: .012, color: "#d4c74a" },
  { label: "Lớp + TV", x: .22, y: .50, width: .31, height: .065, elevation: .10, color: "#e5ece9", roof: "#973a38" },
  { label: "Hiệu bộ", x: .43, y: .61, width: .11, height: .13, elevation: .12, color: "#e7efeb", roof: "#973a38" },
  { label: "Sân VĐ", x: .68, y: .45, width: .24, height: .31, elevation: .015, color: "#5aa873" },
  { label: "Sân", x: .25, y: .62, width: .26, height: .17, elevation: .012, color: "#ded3bb" },
  { label: "10 phòng", x: .27, y: .80, width: .29, height: .065, elevation: .10, color: "#e5ece9", roof: "#973a38" },
  { label: "Đa năng", x: .62, y: .80, width: .15, height: .14, elevation: .07, color: "#eadfc9", roof: "#a9433c" },
  { label: "Hồ nước", x: .79, y: .82, width: .15, height: .11, elevation: .008, color: "#71afbb" },
  { label: "Nhà xe", x: .09, y: .68, width: .055, height: .23, elevation: .04, color: "#ddd4c2", roof: "#a9433c" }
];

export default function CampusMap({ mode, reports, zoom }: CampusMapProps) {
  const googleZoom = Math.max(15, Math.min(20, 18 + Math.round((zoom - 1) * 8)));
  const mapUrl = useMemo(
    () => `https://www.google.com/maps?q=${SCHOOL.lat},${SCHOOL.lon}&z=${googleZoom}&output=embed`,
    [googleZoom]
  );

  return (
    <div className={`real-campus-map ${mode}`}>
      {mode === "gps" ? (
        <iframe
          key={mapUrl}
          className="google-map-frame"
          src={mapUrl}
          title="Google Maps - Trường PTDTNT THPT Đam San"
          loading="eager"
          referrerPolicy="no-referrer-when-downgrade"
        />
      ) : (
        <CampusCanvas reports={reports} zoom={zoom} />
      )}
    </div>
  );
}

function CampusCanvas({ reports, zoom }: { reports: TrashReport[]; zoom: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [rotation, setRotation] = useState(248);
  const drag = useRef<{ x: number; rotation: number } | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const parent = canvas.parentElement;
    if (!parent) return;

    const render = () => drawCampus(canvas, parent.clientWidth, parent.clientHeight, rotation, zoom, reports);
    const observer = new ResizeObserver(render);
    observer.observe(parent);
    render();
    return () => observer.disconnect();
  }, [reports, rotation, zoom]);

  return (
    <canvas
      ref={canvasRef}
      className="campus-canvas"
      aria-label="Bản đồ 3D tương tác của khuôn viên trường"
      onPointerDown={(event) => {
        drag.current = { x: event.clientX, rotation };
        event.currentTarget.setPointerCapture(event.pointerId);
      }}
      onPointerMove={(event) => {
        if (!drag.current) return;
        setRotation(drag.current.rotation + (event.clientX - drag.current.x) * .32);
      }}
      onPointerUp={(event) => {
        drag.current = null;
        event.currentTarget.releasePointerCapture(event.pointerId);
      }}
      onPointerCancel={() => { drag.current = null; }}
    />
  );
}

function drawCampus(canvas: HTMLCanvasElement, width: number, height: number, rotation: number, zoom: number, reports: TrashReport[]) {
  const ratio = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = Math.max(1, Math.round(width * ratio));
  canvas.height = Math.max(1, Math.round(height * ratio));
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  const context = canvas.getContext("2d");
  if (!context) return;
  context.scale(ratio, ratio);

  const gradient = context.createLinearGradient(0, 0, 0, height);
  gradient.addColorStop(0, "#d9f1e8");
  gradient.addColorStop(.58, "#f4f7ee");
  gradient.addColorStop(1, "#d4e4d7");
  context.fillStyle = gradient;
  context.fillRect(0, 0, width, height);

  const scale = Math.min(width * .9, height * 1.15) * zoom;
  const originX = width * .50;
  const originY = height * .10;
  const radians = rotation * Math.PI / 180;
  const project = (x: number, y: number, z = 0) => {
    const dx = x - .5;
    const dy = y - .5;
    const rx = dx * Math.cos(radians) - dy * Math.sin(radians) + .5;
    const ry = dx * Math.sin(radians) + dy * Math.cos(radians) + .5;
    return { x: originX + (rx - ry) * scale * .72, y: originY + (rx + ry) * scale * .36 - z * scale };
  };

  const polygon = (points: Array<{ x: number; y: number }>, fill: string, stroke?: string) => {
    context.beginPath();
    points.forEach((point, index) => index ? context.lineTo(point.x, point.y) : context.moveTo(point.x, point.y));
    context.closePath();
    context.fillStyle = fill;
    context.fill();
    if (stroke) { context.strokeStyle = stroke; context.lineWidth = 1; context.stroke(); }
  };

  context.save();
  context.shadowColor = "rgba(20,55,43,.22)";
  context.shadowBlur = 22;
  context.fillStyle = "rgba(31,72,55,.22)";
  context.beginPath();
  context.ellipse(width * .5, height * .68, scale * .58, scale * .2, 0, 0, Math.PI * 2);
  context.fill();
  context.restore();

  polygon([project(.05, .04), project(.94, .04), project(.96, .95), project(.06, .96)], "#cbdab6", "#6c9473");

  const roads: Block[] = [
    { label: "", x: .47, y: .04, width: .09, height: .91, elevation: .006, color: "#eee8d9" },
    { label: "", x: .05, y: .47, width: .90, height: .09, elevation: .006, color: "#eee8d9" },
    { label: "", x: .05, y: .04, width: .90, height: .045, elevation: .006, color: "#e5e5df" },
    { label: "", x: .05, y: .92, width: .90, height: .045, elevation: .006, color: "#e5e5df" }
  ];

  const drawBlock = (block: Block) => {
    const bottom = [project(block.x, block.y), project(block.x + block.width, block.y), project(block.x + block.width, block.y + block.height), project(block.x, block.y + block.height)];
    const top = [project(block.x, block.y, block.elevation), project(block.x + block.width, block.y, block.elevation), project(block.x + block.width, block.y + block.height, block.elevation), project(block.x, block.y + block.height, block.elevation)];
    if (block.elevation > .025) {
      polygon([top[1], bottom[1], bottom[2], top[2]], shade(block.color, -.20));
      polygon([top[2], bottom[2], bottom[3], top[3]], shade(block.color, -.30));
    }
    polygon(top, block.color, "rgba(42,71,58,.22)");
    if (block.roof) {
      const roof = [project(block.x - .008, block.y - .008, block.elevation + .022), project(block.x + block.width + .008, block.y - .008, block.elevation + .022), project(block.x + block.width + .008, block.y + block.height + .008, block.elevation + .022), project(block.x - .008, block.y + block.height + .008, block.elevation + .022)];
      polygon(roof, block.roof, "rgba(90,35,31,.35)");
    }
    if (block.label) {
      const center = project(block.x + block.width / 2, block.y + block.height / 2, block.elevation + .03);
      context.fillStyle = "#263d34";
      context.font = `700 ${Math.max(7, Math.min(10, width / 42))}px Arial`;
      context.textAlign = "center";
      context.fillText(block.label, center.x, center.y);
    }
  };

  roads.forEach(drawBlock);
  [...blocks].sort((a, b) => a.x + a.y - b.x - b.y).forEach(drawBlock);

  for (let index = 0; index < 34; index++) {
    const x = .08 + ((index * 37) % 83) / 100;
    const y = .08 + ((index * 61) % 82) / 100;
    const point = project(x, y, .035);
    context.fillStyle = index % 3 === 0 ? "#24794b" : "#36945b";
    context.beginPath();
    context.arc(point.x, point.y, Math.max(2.5, width / 110), 0, Math.PI * 2);
    context.fill();
  }

  const approved = reports.filter((report) => report.status === "approved").slice(0, 8);
  const markers = approved.length ? approved : [{ className: "Hiệu bộ" } as TrashReport];
  markers.forEach((report, index) => {
    const x = .48 + ((index % 3) - 1) * .09;
    const y = .58 + Math.floor(index / 3) * .07;
    const point = project(x, y, .16);
    drawPin(context, point.x, point.y, report.className || "Báo cáo", index === 0 ? "#ed8d2d" : "#268f62", width);
  });
}

function drawPin(context: CanvasRenderingContext2D, x: number, y: number, label: string, color: string, width: number) {
  const radius = Math.max(7, width / 36);
  context.fillStyle = color;
  context.beginPath();
  context.arc(x, y, radius, Math.PI, 0);
  context.lineTo(x, y + radius * 1.8);
  context.closePath();
  context.fill();
  context.fillStyle = "white";
  context.beginPath();
  context.arc(x, y, radius * .35, 0, Math.PI * 2);
  context.fill();
  context.font = `700 ${Math.max(7, Math.min(10, width / 42))}px Arial`;
  context.textAlign = "center";
  const text = label.slice(0, 10);
  const textWidth = context.measureText(text).width + 12;
  context.fillStyle = "rgba(255,255,255,.92)";
  context.fillRect(x - textWidth / 2, y + radius * 2, textWidth, 16);
  context.fillStyle = "#244238";
  context.fillText(text, x, y + radius * 2 + 11);
}

function shade(hex: string, amount: number) {
  const value = Number.parseInt(hex.slice(1), 16);
  const adjust = (channel: number) => Math.max(0, Math.min(255, Math.round(channel * (1 + amount))));
  return `rgb(${adjust(value >> 16)}, ${adjust((value >> 8) & 255)}, ${adjust(value & 255)})`;
}
