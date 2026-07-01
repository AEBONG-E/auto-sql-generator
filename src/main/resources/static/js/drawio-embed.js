(function (global) {
  "use strict";

  var DEFAULT_ORIGIN = "https://app.diagrams.net";

  function asString(value) {
    if (value === null || value === undefined) {
      return "";
    }
    return String(value);
  }

  function escapeXml(value) {
    return asString(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&apos;");
  }

  function ensureArray(value) {
    return Array.isArray(value) ? value : [];
  }

  function tableId(table, index) {
    var raw = table && (table.id || table.tableId || table.tableName || table.name);
    return raw ? asString(raw) : "table_" + index;
  }

  function tableName(table, index) {
    return asString((table && (table.name || table.tableName)) || ("Table " + (index + 1)));
  }

  function tableColumns(table) {
    var columns = ensureArray(table && (table.columns || table.fields));
    if (columns.length === 0) {
      return "";
    }
    var lines = [];
    for (var i = 0; i < columns.length; i += 1) {
      var col = columns[i] || {};
      var colName = asString(col.name || col.columnName || ("column_" + (i + 1)));
      var colType = asString(col.type || col.dataType || "");
      var suffix = colType ? " : " + colType : "";
      lines.push(colName + suffix);
    }
    return lines.join("\n");
  }

  function buildTableValue(table, index) {
    var title = tableName(table, index);
    var cols = tableColumns(table);
    if (!cols) {
      return escapeXml(title);
    }
    return escapeXml(title + "\n--------------------\n" + cols).replace(/\n/g, "&#xa;");
  }

  function buildDrawioXml(payload, options) {
    var data = payload || {};
    var opts = options || {};
    var tables = ensureArray(data.tables);
    var relations = ensureArray(data.relations);
    var cellLines = [];
    var tableCellMap = {};

    var startX = Number(opts.startX) || 40;
    var startY = Number(opts.startY) || 40;
    var boxWidth = Number(opts.boxWidth) || 240;
    var defaultBoxHeight = Number(opts.boxHeight) || 0; // 0 = 동적 계산
    var gapX = Number(opts.gapX) || 300;
    var rowGap = Number(opts.rowGap) || Number(opts.gapY) || 40; // 하위 호환: gapY fallback
    var perRow = Number(opts.perRow) || 3;

    // 각 테이블 높이 사전 계산
    // 헤더 28px + (구분선 1줄 + 컬럼 수) × 14px
    var tableHeights = [];
    for (var k = 0; k < tables.length; k += 1) {
      var colCount = ensureArray((tables[k] || {}).columns || (tables[k] || {}).fields).length;
      tableHeights.push(
        defaultBoxHeight > 0 ? defaultBoxHeight : Math.max(42, 28 + (colCount + 1) * 14)
      );
    }

    // 행별 Y 오프셋 계산 (각 행의 최대 테이블 높이 기준)
    var rowYOffsets = [];
    var currentY = startY;
    for (var r = 0; r * perRow < tables.length; r += 1) {
      rowYOffsets.push(currentY);
      var maxHeightInRow = 0;
      for (var c = 0; c < perRow && r * perRow + c < tables.length; c += 1) {
        maxHeightInRow = Math.max(maxHeightInRow, tableHeights[r * perRow + c]);
      }
      currentY += maxHeightInRow + rowGap;
    }

    for (var i = 0; i < tables.length; i += 1) {
      var table = tables[i] || {};
      var logicalId = tableId(table, i);
      var cellId = "tbl_" + logicalId.replace(/[^\w\-:.]/g, "_");
      var row = Math.floor(i / perRow);
      var col = i % perRow;
      var x = startX + col * gapX;
      var y = rowYOffsets[row];
      var tableHeight = tableHeights[i];
      var value = buildTableValue(table, i);

      tableCellMap[logicalId] = cellId;

      cellLines.push(
        '<mxCell id="' + escapeXml(cellId) + '" value="' + value + '" style="shape=swimlane;childLayout=stackLayout;horizontal=1;startSize=28;rounded=0;fillColor=#23273a;swimlaneFillColor=#1a1d27;strokeColor=#3a3f55;fontColor=#e4e6f0;fontStyle=1;align=left;verticalAlign=top;" vertex="1" parent="1">' +
          '<mxGeometry x="' + x + '" y="' + y + '" width="' + boxWidth + '" height="' + tableHeight + '" as="geometry"/>' +
        "</mxCell>"
      );
    }

    for (var j = 0; j < relations.length; j += 1) {
      var relation = relations[j] || {};
      var fromKey = asString(relation.from || relation.source || relation.fromTable || relation.fromTableId);
      var toKey = asString(relation.to || relation.target || relation.toTable || relation.toTableId);
      var sourceId = tableCellMap[fromKey];
      var targetId = tableCellMap[toKey];

      if (!sourceId || !targetId) {
        continue;
      }

      var relLabel = asString(relation.label || relation.type || "");
      var edgeId = "rel_" + j;

      cellLines.push(
        '<mxCell id="' + edgeId + '" value="' + escapeXml(relLabel) + '" style="endArrow=block;html=1;rounded=0;orthogonalLoop=1;jettySize=auto;strokeColor=#5b8dee;fontColor=#8b90a8;" edge="1" parent="1" source="' + escapeXml(sourceId) + '" target="' + escapeXml(targetId) + '">' +
          '<mxGeometry relative="1" as="geometry"/>' +
        "</mxCell>"
      );
    }

    return (
      '<mxfile host="app.diagrams.net"><diagram name="ERD"><mxGraphModel dx="1200" dy="800" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1600" pageHeight="1200" math="0" shadow="0"><root>' +
      '<mxCell id="0"/><mxCell id="1" parent="0"/>' +
      cellLines.join("") +
      "</root></mxGraphModel></diagram></mxfile>"
    );
  }

  function DrawioEmbedClient(config) {
    var cfg = config || {};
    this.iframe = cfg.iframe || null;
    this.origin = cfg.origin || DEFAULT_ORIGIN;
    this.onMessage = typeof cfg.onMessage === "function" ? cfg.onMessage : null;
    this._boundMessage = this._handleMessage.bind(this);
    this._connected = false;
  }

  DrawioEmbedClient.prototype.connect = function () {
    if (!this.iframe || !this.iframe.contentWindow || this._connected) {
      return false;
    }
    global.addEventListener("message", this._boundMessage);
    this._connected = true;
    return true;
  };

  DrawioEmbedClient.prototype.disconnect = function () {
    if (!this._connected) {
      return;
    }
    global.removeEventListener("message", this._boundMessage);
    this._connected = false;
  };

  DrawioEmbedClient.prototype._handleMessage = function (event) {
    if (!event || event.origin !== this.origin) {
      return;
    }

    var data = event.data;
    if (typeof data === "string") {
      try {
        data = JSON.parse(data);
      } catch (e) {
        return;
      }
    }

    if (this.onMessage) {
      this.onMessage(data, event);
    }
  };

  DrawioEmbedClient.prototype.send = function (message) {
    if (!this.iframe || !this.iframe.contentWindow || !message) {
      return false;
    }
    this.iframe.contentWindow.postMessage(JSON.stringify(message), this.origin);
    return true;
  };

  DrawioEmbedClient.prototype.loadXml = function (xml) {
    return this.send({ action: "load", xml: asString(xml) });
  };

  DrawioEmbedClient.prototype.loadFromData = function (data, options) {
    var xml = buildDrawioXml(data, options);
    return this.loadXml(xml);
  };

  global.AutoErdDrawio = {
    DrawioEmbedClient: DrawioEmbedClient,
    buildDrawioXml: buildDrawioXml,
    escapeXml: escapeXml
  };
})(window);
