var Messages = {
    panelId: "パネルID",
    watt: "電力 [W]",
    measuredTime: "測定時刻"
};

window.console = window.console || {
        log: function () {
        },
        error: function () {
        }
    };

/** HandsontableとChart.jsのangular用モジュールを取り込み
 *  Handsontable : パネル一覧テーブルを作成
 *  Chart.js : （ある一つの）パネルの発電電力推移をグラフとして表示
 */
var app = angular.module("energy-monitor", ["ngHandsontable", "chart.js"]);

/**
 * サーバーとSocket通信をするサービス
 */
app.service("WebSocketService", ["$http", "$timeout", function ($http, $timeout) {
        var eventListenerMap = {};
        var Events = {
            ALERT: "alert",
            MEASUREMENT: "measurement"
        };
        this.Events = Events;
        var eventProcessOrder = [Events.ALERT, Events.MEASUREMENT];
        var socket = new WebSocket(document.querySelector("[ng-app=energy-monitor]").getAttribute("data-ws-url"))

        /** サーバーからデータを受信した時に呼ばれる関数の登録
         * @param type
         * WebSocketService.Events.ALERT or WebSocketService.Events.MEASUREMENT
         * @param listener
         * listenerに渡される引数は受け取ったデータ
         */
        this.addMessageListener = function (type, listener) {
            if (!eventListenerMap[type]) eventListenerMap[type] = [];
            eventListenerMap[type].push(listener);
        };

        socket.onopen = function () {
            console.log("Opning a connection ...")
        };

        socket.onmessage = function (event) {
            // $timeout(fn)は$scope.$apply()の代わり（描画更新用）
            $timeout(function () {
                var jsonParsed = JSON.parse(event.data);
                angular.forEach(eventProcessOrder, function (type) {
                    if (jsonParsed[type]) {
                        angular.forEach(eventListenerMap[type], function (listener, index) {
                            listener(jsonParsed[type]);
                        });
                    }
                });
            });
        };

        socket.onerror = function (event) {
            console.error("Error: " + event.data)
        };

        socket.onclose = function () {
            console.log("Connection closed.")
        };

    }]
);

/**
 * 一覧テーブルに関する処理を行なうサービス
 */
app.service("MonitorService", ["WebSocketService", function (WebSocketService) {
        var currentData = [];

        var hotInstance = null;
        this.saveInstance = function (instance) {
            hotInstance = instance;
        };

        this.selectByScript = false;
        this.selectRowByPanelId = function (panelId) {
            for (var rowNumber = 0, len = currentData.length; rowNumber < len; rowNumber++) {
                if (currentData[rowNumber].panelId === panelId) {
                    this.selectRow(rowNumber);
                    break;
                }
            }
        };
        this.selectRow = function (rowNumber) {
            this.selectByScript = true;
            hotInstance.selectCell(rowNumber, 0);
            this.selectByScript = false;
        };

        this.rowNumberToPanelId = function (rowNumber) {
            return currentData[rowNumber].panelId;
        };

        var renderFunction = null;
        this.setRenderFunction = function (func) {
            renderFunction = func;
        };
        WebSocketService.addMessageListener(WebSocketService.Events.MEASUREMENT, function (panels) {
            currentData = panels;
            if (renderFunction) {
                renderFunction(panels);
            }
        });
    }]
);

/**
 * 一覧テーブルを描画するコントローラー
 */
app.controller("Monitor", ["$scope", "$filter", "MonitorService", "ChartService", function ($scope, $filter, MonitorService, ChartService) {
    $scope.settings = {
//       disableVisualSelection: ["current", "area"]
        // Handsontableに設定を渡すngHandsontableのlink関数はこの部分より早く呼ばれるので、設定をここで定義しているのでは遅い
        // htmlにsettings="{disableVisualSelection: true, currentRowClassName: "currentRow"}"で書く
//       currentRowClassName: "currentRow"
        afterSelectionEnd: function (rowNumStart /*, colNumStart, rowNumEnd, colNumEnd */) {
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
        afterLoadData: function () {
            MonitorService.saveInstance(this);
        }
    };

    var dateFilter = $filter("date");
    // "yyyy/MM/dd HH:mm:ss"形式
    var DATE_FORMAT = "HH:mm:ss";

    function timeRenderer(instance, td, row, col, prop, value, cellProperties) {
        arguments[5] = dateFilter(value, DATE_FORMAT);
        Handsontable.renderers.TextRenderer.apply(this, arguments);
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
    MonitorService.setRenderFunction(function (panels) {
        // 参照を解除してはならないのでspliceで元の配列への参照は維持
        $scope.panels.splice(0);
        angular.forEach(panels, function (panel) {
            $scope.panels.push(panel);
        });
    });
}]);

/**
 * パネルの発電電力推移のグラフに関する処理を行なうサービス
 */
app.service("ChartService", ["$filter", "WebSocketService", "MonitorService", function ($filter, WebSocketService, MonitorService) {
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
        // 1秒間を何分割するか（1000の約数以外は動作保証外）
        var NUMBER_OF_SEPARATE_SECOND = 10;
        var SHOW_CHART_PERIOD = SHOW_CHART_SECOND * NUMBER_OF_SEPARATE_SECOND;
        var ONE_PERIOD_MILLI_SECOND = 1000 / NUMBER_OF_SEPARATE_SECOND;

        var dateFilter = $filter("date");
        var DATE_FORMAT = "HH:mm:ss";


        // 線の種類の名前（1種類）
        var series = [];
        // x軸（時刻）
        var labels = [];
        // y軸（watt）
        var watts = [];

        var graphAppeared = false;
        var ChartService = this;

        var latestXLabelTime = null;
        var indexInSecond = 0;

        function pushLabel() {
            indexInSecond++;
            if (indexInSecond % NUMBER_OF_SEPARATE_SECOND === 0) {
                // x軸には1秒間隔の時刻を表示する
                latestXLabelTime += 1000;
                labels.push(dateFilter(latestXLabelTime, DATE_FORMAT));
                indexInSecond = 0;
            } else {
                labels.push("");
            }
        }

        WebSocketService.addMessageListener(WebSocketService.Events.MEASUREMENT, function (panels) {
            if (!graphAppeared && panels.length > 0) {
                graphAppeared = true;
                var panelId = panels[0].panelId;
                MonitorService.selectRowByPanelId(panelId);
                ChartService.show(panelId);
            }
            if (targetPanelId !== null) {
                for (var i = 0, len = panels.length; i < len; i++) {
                    var panel = panels[i];
                    if (panel.panelId === targetPanelId) {
                        var measuredTime = panel.measuredTime;
                        if (latestXLabelTime === null) {
                            // 初回観測時間のミリ秒切り捨て時刻
                            latestXLabelTime = (measuredTime / 1000 | 0) * 1000;
                            // pushLabelで+1000されるため
                            latestXLabelTime -= 1000;
                        }

                        var diff_ms = measuredTime - (latestXLabelTime + indexInSecond * ONE_PERIOD_MILLI_SECOND);
                        var diff_period = Math.round(diff_ms / ONE_PERIOD_MILLI_SECOND);
                        for (var j = 1; j < diff_period; j++) {
                            pushLabel();
                            watts.push(null);
                        }
                        pushLabel();
                        watts.push(panel.watt);

                        // 後ろから最大 SHOW_CHART_PERIOD 個だけ残す
                        labels.splice(0, labels.length - SHOW_CHART_PERIOD);
                        var destroyedValues = watts.splice(0, watts.length - SHOW_CHART_PERIOD);

                        // y軸付近に空白が発生するのを回避するために補間する
                        var oldestValue = null;
                        var oldestValueIndex = 0;
                        for (var j = 0, len_j = watts.length; j < len_j; j++) {
                            if (watts[j] !== null) {
                                oldestValue = watts[j];
                                oldestValueIndex = j;
                                break;
                            }
                        }
                        if (oldestValue !== null) {
                            var latestDestroyedValue = null;
                            var latestDestroyedValueIndex = null;
                            for (var j = 0, len_j = destroyedValues.length; j < len_j; j++) {
                                if (destroyedValues[j] !== null) {
                                    latestDestroyedValue = destroyedValues[j];
                                    latestDestroyedValueIndex = j;
                                }
                            }
                            if (latestDestroyedValue !== null) {
                                var span = destroyedValues.length - latestDestroyedValueIndex + oldestValueIndex;
                                var interpolatedValue = oldestValue - (oldestValue - latestDestroyedValue) / span * oldestValueIndex;
                                watts[0] = interpolatedValue;
                            }
                        }
                        // 補間ここまで

                        angular.forEach(eventListenerMap[Events.UPDATE], function (fn, index) {
                            fn();
                        });
                        return;
                    }
                }
                // データがなかった場合の処理
                angular.forEach(eventListenerMap[Events.NOT_FOUND], function (fn, index) {
                    fn();
                });
            }
        });

        this.getChartData = function () {
            // 参照渡しでデータの変更は検知してくれるので、データの受信ごとではなく1回だけの実行でOK
            var data = [watts];
            return {
                series: series,
                labels: labels,
                data:data
            };
        };

        this.addChartEventListener = function (type, listener) {
            if (!eventListenerMap[type]) eventListenerMap[type] = [];
            eventListenerMap[type].push(listener);
        };

        this.show = function (panelId) {
            if (targetPanelId !== panelId) {
                targetPanelId = panelId;
                // 保存済みデータの初期化
                series.splice(0);
                labels.splice(0);
                watts.splice(0);
                latestXLabelTime = null;

                series.push(targetPanelId);
                for (var i = 0; i < SHOW_CHART_PERIOD; i++) {
                    labels.push("");
                    watts.push(null);
                }

                angular.forEach(eventListenerMap[Events.SHOW], function (fn, index) {
                    fn(targetPanelId);
                });
            }
        };
    }]
);


/**
 * Chart.jsの設定
 */
app.config(["ChartJsProvider", function (ChartJsProvider) {
    // http://www.chartjs.org/docs/
    // Configure Line charts
    ChartJsProvider.setOptions("Line", {
        scaleBeginAtZero: true,
        animationSteps: 1,
        bezierCurve: false,
        scaleShowVerticalLines: false,
        scaleShowHorizontalLines: true,
        showTooltips: false,
        pointDot: false
    });
}]);

/**
 * パネルの発電電力推移のグラフを描画するコントローラー
 */
app.controller("LineChart", ["$scope", "ChartService", function ($scope, ChartService) {
    var chartData = ChartService.getChartData();
    $scope.series = chartData.series;
    $scope.labels = chartData.labels;
    $scope.data = chartData.data;

    ChartService.addChartEventListener(ChartService.Events.SHOW, function (targetPanelId) {
        $scope.title = targetPanelId;
    });
    ChartService.addChartEventListener(ChartService.Events.UPDATE, function () {
        $scope.nodata = false;
    });
    ChartService.addChartEventListener(ChartService.Events.NOT_FOUND, function () {
        $scope.nodata = true;
    });
}]);

/**
 * アラートを描画するコントローラー
 */
app.controller("AlertNotification", ["$scope", "WebSocketService", "ChartService", "MonitorService", function ($scope, WebSocketService, ChartService, MonitorService) {
    $scope.alertCount = 0;
    $scope.alertedPanels = [];

    $scope.showAlertInfo = function () {
        document.getElementById("alert-detail").open();
    };
    $scope.delete = function (panelId) {
        var index = alertedPanelIds.indexOf(panelId);
        if (index !== -1) {
            alertedPanelIds.splice(index, 1);
            delete alertedPanelInfos[panelId];
            refresh();
        }
    };
    $scope.showChart = function (panelId) {
        MonitorService.selectRowByPanelId(panelId);
        ChartService.show(panelId);
        document.getElementById("alert-detail").close();
    };


    var alertedPanelIds = [];
    var alertedPanelInfos = {};
    WebSocketService.addMessageListener(WebSocketService.Events.ALERT, function (panels) {
        angular.forEach(panels, function (panel) {
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
            alertedPanelIds.sort(function (id_a, id_b) {
                return alertedPanelInfos[id_b].measuredTime - alertedPanelInfos[id_a].measuredTime;
            });
        });
        refresh();
    });

    function refresh() {
        var alertedPanels = [];
        angular.forEach(alertedPanelIds, function (panelId) {
            var panelInfo = alertedPanelInfos[panelId];
            alertedPanels.push(panelInfo);
        });

        $scope.alertedPanels = alertedPanels;
        $scope.alertCount = alertedPanels.length;
    }
}]);