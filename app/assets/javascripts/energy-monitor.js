var Messages = {
    panelId: "パネルID",
    watt: "電力 [W]",
    measuredTime: "測定時刻",
    lowerLimit: "下限 [W]"
};

window.console = window.console || {
        log: function() {
        },
        error: function() {
        }
    };

/** HandsontableとChart.jsのangular用モジュールを取り込み
 *  Handsontable : パネル一覧テーブルを作成
 *  Chart.js : （ある一つの）パネルの発電電力推移をグラフとして表示
 */
var app = angular.module("energy-monitor", ["ngHandsontable", "Chart.Scatter.js"]);

/**
 * サーバーとSocket通信をするサービス
 */
app.service("WebSocketService", ["$http", "$timeout", function($http, $timeout) {
        var eventListenerMap = {};
        var Events = {
            ALERT: "alert",
            MEASUREMENT: "measurement",
            ERROR: "error",
            LOWER_LIMIT: "lowerLimit"
        };
        this.Events = Events;
        var eventProcessOrder = [Events.ALERT, Events.MEASUREMENT, Events.LOWER_LIMIT, Events.ERROR];
        var socket = new WebSocket(document.querySelector("[ng-app=energy-monitor]").getAttribute("data-ws-url"));

        /** サーバーからデータを受信した時に呼ばれる関数の登録
         * @param type
         * WebSocketService.Events.ALERT or WebSocketService.Events.MEASUREMENT or WebSocketService.Events.ERROR
         * @param listener
         * listenerに渡される引数は受け取ったデータ
         */
        this.addMessageListener = function(type, listener) {
            if (!eventListenerMap[type]) eventListenerMap[type] = [];
            eventListenerMap[type].push(listener);
        };

        socket.onopen = function() {
            console.log("Opning a connection ...");
        };

        socket.onmessage = function(event) {
            // $timeout(fn)は$scope.$apply()の代わり（描画更新用）
            $timeout(function() {
                var jsonParsed = JSON.parse(event.data);
                angular.forEach(eventProcessOrder, function(type) {
                    if (jsonParsed[type]) {
                        angular.forEach(eventListenerMap[type], function(listener, index) {
                            listener(jsonParsed[type]);
                        });
                    }
                });
            });
        };

        socket.onerror = function(event) {
            console.error("Error: " + event.data);
        };

        socket.onclose = function() {
            console.log("Connection closed.");
        };
    }]
);

/**
 * 一覧テーブルに関する処理を行なうサービス
 */
app.service("MonitorService", ["WebSocketService", function(WebSocketService) {
        var currentData = [];

        var hotInstance = null;
        this.saveInstance = function(instance) {
            hotInstance = instance;
        };

        this.selectByScript = false;
        this.selectRowByPanelId = function(panelId) {
            for (var rowNumber = 0, len = currentData.length; rowNumber < len; rowNumber++) {
                if (currentData[rowNumber].panelId === panelId) {
                    this.selectRow(rowNumber);
                    break;
                }
            }
        };
        this.selectRow = function(rowNumber) {
            this.selectByScript = true;
            hotInstance.selectCell(rowNumber, 0);
            this.selectByScript = false;
        };

        this.rowNumberToPanelId = function(rowNumber) {
            return currentData[rowNumber].panelId;
        };

        var renderFunction = null;
        this.setRenderFunction = function(func) {
            renderFunction = func;
        };
        WebSocketService.addMessageListener(WebSocketService.Events.MEASUREMENT, function(panels) {
            currentData = panels;
            if (renderFunction) {
                renderFunction(panels);
            }
        });
    }]
);

/**
 * バックエンドのエラーに関する処理を行なうサービス
 */
app.service("BackendErrorService", ["WebSocketService", function (WebSocketService) {

        var onErrorCallback;
        this.onError = function(callback) {
            onErrorCallback = callback;
        };

        var onRecoverCallback;
        this.onRecover = function(callback) {
            onRecoverCallback = callback;
        };

        WebSocketService.addMessageListener(WebSocketService.Events.ERROR, function (error) {
            if (onErrorCallback) { onErrorCallback(error); }
        });

        WebSocketService.addMessageListener(WebSocketService.Events.MEASUREMENT, function () {
            if (onRecoverCallback) { onRecoverCallback(); }
        });
    }]
);

/**
 * 一覧テーブルを描画するコントローラー
 */
app.controller("Monitor", ["$scope", "$filter", "MonitorService", "ChartService", function($scope, $filter, MonitorService, ChartService) {
    $scope.settings = {
//       disableVisualSelection: ["current", "area"]
        // Handsontableに設定を渡すngHandsontableのlink関数はこの部分より早く呼ばれるので、設定をここで定義しているのでは遅い
        // htmlにsettings="{disableVisualSelection: true, currentRowClassName: "currentRow"}"で書く
//       currentRowClassName: "currentRow"
        afterSelectionEnd: function(rowNumStart /*, colNumStart, rowNumEnd, colNumEnd */) {
            if (!MonitorService.selectByScript) {
                var panelId = MonitorService.rowNumberToPanelId(rowNumStart);
                ChartService.show(panelId);
                // これはng-clickによって呼ばれるものではないので描画更新が必要
                $scope.$apply();

                /* Handsontable 0.13.1（0.14未満）の場合用：複数選択時に選択セルは最後にマウスを載せた場所ではなく最初におろした場所であるため
                 * Handsontable 0.14.xの場合、ホイールスクロール時にエラー（ただし実害はない）が発生するため0.13.1に戻した
                 */
                /* selectCellは afterSelectionEnd 関数を呼ぶので無限ループにならないように */
                MonitorService.selectRow(rowNumStart);
            }
        },
        afterLoadData: function() {
            MonitorService.saveInstance(this);
        }
    };

    var dateFilter = $filter("date");
    // "yyyy/MM/dd HH:mm:ss"形式
    var DATE_FORMAT = "HH:mm:ss";

    function timeRenderer(instance, td, row, col, prop, value, cellProperties) {
        value = dateFilter(value, DATE_FORMAT);
        Handsontable.renderers.TextRenderer.apply(this, [instance, td, row, col, prop, value, cellProperties]);
    }

    $scope.columns = [
        {
            data: "panelId",
            title: Messages.panelId,
            readOnly: true
        },
        {
            data: "watt",
            title: Messages.watt,
            type: "numeric",
            format: "0,0.00",
            readOnly: true
        },
        {
            data: "measuredTime",
            title: Messages.measuredTime,
            renderer: timeRenderer,
            readOnly: true
        }
    ];

    // テーブルに表示するデータは初回に存在する必要があり、その参照がHandontableに渡る
    $scope.panels = [];
    MonitorService.setRenderFunction(function(panels) {
        // 参照を解除してはならないのでspliceで元の配列への参照は維持
        $scope.panels.splice(0);
        angular.forEach(panels, function(panel) {
            $scope.panels.push(panel);
        });
    });
}]);

/**
 * パネルの発電電力推移のグラフに関する処理を行なうサービス
 */
app.service("ChartService", ["WebSocketService", "MonitorService", function(WebSocketService, MonitorService) {
        var eventListenerMap = {};
        var Events = {
            SHOW: "show",
            UPDATE: "update",
            NOT_FOUND: "not_found"
        };
        this.Events = Events;

        var targetPanelId = null;
        // 何秒間のデータを表示するか
        var SHOW_CHART_SECOND = 30;

        var measuredData = [];
        var lowerLimitData = [];

        var latestTime = null;

        function discardOldData() {
            var thresholdBeforeTimeDiscard = latestTime - 1000 * SHOW_CHART_SECOND;
            angular.forEach([measuredData, lowerLimitData], function(data) {
                for (var j = 0; j < data.length; j++) {
                    if (data[j].x < thresholdBeforeTimeDiscard) {
                        data.splice(0, 1);
                        j--;
                    } else {
                        break;
                    }
                }
            });
        }

        var graphAppeared = false;
        var ChartService = this;
        WebSocketService.addMessageListener(WebSocketService.Events.LOWER_LIMIT, function(info) {
            if (targetPanelId !== null) {
                latestTime = info.time;
                lowerLimitData.push({
                    x: info.detectedDateTime,
                    y: info.value
                });
                discardOldData();
            }
        });

        WebSocketService.addMessageListener(WebSocketService.Events.MEASUREMENT, function(panels) {
            if (!graphAppeared && panels.length > 0) {
                graphAppeared = true;
                var panelId = panels[0].panelId;
                MonitorService.selectRowByPanelId(panelId);
                ChartService.show(panelId);
            }
            if (targetPanelId !== null) {
                var existData = false;
                for (var i = 0, len = panels.length; i < len; i++) {
                    var panel = panels[i];
                    if (panel.panelId === targetPanelId) {
                        existData = true;
                        var measuredTime = panel.measuredTime;
                        latestTime = measuredTime;
                        measuredData.push({
                            x: measuredTime,
                            y: panel.watt
                        });
                        // 最大で SHOW_CHART_SECOND 秒のデータだけ表示する
                        discardOldData();
                        break;
                    }
                }

                angular.forEach(eventListenerMap[existData ? Events.UPDATE : Events.NOT_FOUND], function(fn, index) {
                    fn();
                });
            }
        });

        this.getChartData = function() {
            // 参照渡しでデータの変更は検知してくれるので、データの受信ごとではなく1回だけの実行でOK
            return [{
                label: Messages.watt,
                data: measuredData
            }, {
                label: Messages.lowerLimit,
                strokeColor: "pink",
                data: lowerLimitData
            }];
        };

        this.addChartEventListener = function(type, listener) {
            if (!eventListenerMap[type]) eventListenerMap[type] = [];
            eventListenerMap[type].push(listener);
        };

        this.show = function(panelId) {
            if (targetPanelId !== panelId) {
                targetPanelId = panelId;
                // 保存済みデータの初期化
                measuredData.splice(0);
                lowerLimitData.splice(0);

                angular.forEach(eventListenerMap[Events.SHOW], function(fn, index) {
                    fn(targetPanelId);
                });
            }
        };

// this code from Chart.Scatter.js#1.1.2
        Chart.ScatterDateScale.prototype._calculateDateScaleRange = function(valueMin, valueMax, drawingSize, fontSize) {
            // x軸横幅を固定
            valueMin = valueMax - SHOW_CHART_SECOND * 1000;
            var units = [
                // ms単位のメモリが表示されないように単位設定を削除
                {u: 1000, c: 1, t: 1000, n: 's'},
                {u: 1000, c: 2, t: 2000, n: 's'},
                {u: 1000, c: 5, t: 5000, n: 's'},
                {u: 1000, c: 10, t: 10000, n: 's'},
                {u: 1000, c: 15, t: 15000, n: 's'},
                {u: 1000, c: 20, t: 20000, n: 's'},
                {u: 1000, c: 30, t: 30000, n: 's'},
                {u: 60000, c: 1, t: 60000, n: 'm'},
                {u: 60000, c: 2, t: 120000, n: 'm'},
                {u: 60000, c: 5, t: 300000, n: 'm'},
                {u: 60000, c: 10, t: 600000, n: 'm'},
                {u: 60000, c: 15, t: 900000, n: 'm'},
                {u: 60000, c: 20, t: 1200000, n: 'm'},
                {u: 60000, c: 30, t: 1800000, n: 'm'},
                {u: 3600000, c: 1, t: 3600000, n: 'h'},
                {u: 3600000, c: 2, t: 7200000, n: 'h'},
                {u: 3600000, c: 3, t: 10800000, n: 'h'},
                {u: 3600000, c: 4, t: 14400000, n: 'h'},
                {u: 3600000, c: 6, t: 21600000, n: 'h'},
                {u: 3600000, c: 8, t: 28800000, n: 'h'},
                {u: 3600000, c: 12, t: 43200000, n: 'h'},
                {u: 86400000, c: 1, t: 86400000, n: 'd'},
                {u: 86400000, c: 2, t: 172800000, n: 'd'},
                {u: 86400000, c: 4, t: 345600000, n: 'd'},
                {u: 86400000, c: 5, t: 432000000, n: 'd'},
                {u: 604800000, c: 1, t: 604800000, n: 'w'}];
            var maxSteps = drawingSize / (fontSize * 3.3);
            var valueRange = +valueMax - valueMin,
                offset = this.useUtc ? 0 : new Date().getTimezoneOffset() * 60000,
                min = +valueMin - offset,
                max = +valueMax - offset;
            var xp = 0, f = [2, 3, 5, 7, 10];
            while (valueRange / units[xp].t > maxSteps) {
                xp++;
                if (xp == units.length) {
                    var last = units[units.length - 1];
                    for (var fp = 0; fp < f.length; fp++) {
                        units.push({
                            u: last.u,
                            c: last.c * f[fp],
                            t: last.c * f[fp] * last.u,
                            n: last.n
                        });
                    }
                }
            }
            var stepValue = units[xp].t,
                start = Math.floor(min / stepValue) * stepValue,
                stepCount = Math.ceil((max - start) / stepValue),
                end = start + stepValue * stepCount;
            return {
                min: start + offset,
                max: end + offset,
                steps: stepCount,
                stepValue: stepValue
            };
        };
    }]
)
;

/**
 * パネルの発電電力推移のグラフを描画するコントローラー
 */
app.controller("LineChart", ["$scope", "$filter", "ChartService", function($scope, $filter, ChartService) {
    var numFilter = $filter("number");
    // カンマ3桁区切り + 小数点以下1桁 形式
    var fractionSize = 1;

    function tooltipTemplate(valuesObject) {
        var watt = numFilter(valuesObject.value, fractionSize);
        var time = valuesObject.argLabel;
        var datasetLabel = valuesObject.datasetLabel;
        return datasetLabel + ": " + watt + " (" + time + ")";
    }

    $scope.data = ChartService.getChartData();
    $scope.options = {
        scaleBeginAtZero: true,
        animationSteps: 1,
        bezierCurve: false,
        scaleShowVerticalLines: true,
        scaleShowHorizontalLines: true,
        datasetStrokeWidth: 3,
        scaleType: "date",
        // x軸メモリ
        scaleTimeFormat: "HH:MM:ss",
        // tooltip表示
        scaleDateTimeFormat: "HH:MM:ss",
        // ローカル時間を使う
        useUtc: false,
        tooltipTemplate: tooltipTemplate,
        multiTooltipTemplate: tooltipTemplate,
        pointDot: false,
//        showTooltips: false
    };

    ChartService.addChartEventListener(ChartService.Events.SHOW, function(targetPanelId) {
        $scope.title = targetPanelId;
    });
    ChartService.addChartEventListener(ChartService.Events.UPDATE, function() {
        $scope.nodata = false;
    });
    ChartService.addChartEventListener(ChartService.Events.NOT_FOUND, function() {
        $scope.nodata = true;
    });
}]);

/**
 * アラートを描画するコントローラー
 */
app.controller("AlertNotification", ["$scope", "WebSocketService", "ChartService", "MonitorService", function($scope, WebSocketService, ChartService, MonitorService) {
    $scope.alertCount = 0;
    $scope.alertedPanels = [];

    $scope.showAlertInfo = function() {
        document.getElementById("alert-detail").open();
    };
    $scope.delete = function(panelId) {
        var index = alertedPanelIds.indexOf(panelId);
        if (index !== -1) {
            alertedPanelIds.splice(index, 1);
            delete alertedPanelInfos[panelId];
            refresh();
        }
    };
    $scope.showChart = function(panelId) {
        MonitorService.selectRowByPanelId(panelId);
        ChartService.show(panelId);
        document.getElementById("alert-detail").close();
    };


    var alertedPanelIds = [];
    var alertedPanelInfos = {};
    WebSocketService.addMessageListener(WebSocketService.Events.ALERT, function(panels) {
        angular.forEach(panels, function(panel) {
            var panelId = panel.panelId;
            if (alertedPanelIds.indexOf(panelId) === -1) {
                // 前回は壊れてなかった
                alertedPanelIds.unshift(panelId);
                alertedPanelInfos[panelId] = {
                    panelId: panelId,
                    count: 0
                };
            }
            alertedPanelInfos[panelId].measuredTime = panel.measuredTime;
            alertedPanelInfos[panelId].count++;
            // 新しいものが上に来るように
            alertedPanelIds.sort(function(id_a, id_b) {
                return alertedPanelInfos[id_b].measuredTime - alertedPanelInfos[id_a].measuredTime;
            });
        });
        refresh();
    });

    function refresh() {
        var alertedPanels = [];
        angular.forEach(alertedPanelIds, function(panelId) {
            var panelInfo = alertedPanelInfos[panelId];
            alertedPanels.push(panelInfo);
        });

        $scope.alertedPanels = alertedPanels;
        $scope.alertCount = alertedPanels.length;
    }
}]);

/**
 * エラーダイアログのコントローラー
 */
app.controller("ErrorDialog", ["$scope", "BackendErrorService", function ($scope, BackendErrorService) {

    BackendErrorService.onError(function(error) {
        $scope.errorMessage = error.message;
        document.getElementById("error-dialog").open();
    });

    BackendErrorService.onRecover(function() {
        document.getElementById("error-dialog").close();
    });
}]);