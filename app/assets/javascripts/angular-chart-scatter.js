(function(angular, Chart) {
    // 自動リサイズ
    Chart.defaults.global.responsive = true;

    angular.module("Chart.Scatter.js", [])
        // class="chart-scatter"
        .directive("chartScatter", function() {
            return {
                // class
                restrict: "C",
                scope: {
                    data: "=",
                    options: "="
                },
                link: function(scope, elem) {
                    var canvas = elem[0];
                    var chart;

                    scope.$watch("data", resetChart, true);
                    scope.$watch("options", resetChart, true);

                    function resetChart(newVal, oldVal) {
                        if (isEmpty(newVal)) return;
                        if (angular.equals(newVal, oldVal)) return;
                        if (chart) chart.destroy();
                        chart = createChart(scope, canvas);
                        setLegend(canvas, chart);
                    }
                }
            };
        });

    function createChart(scope, canvas) {
        if (!scope.data || !scope.data.length) return;
        var ctx = canvas.getContext("2d");
        var chart = new Chart(ctx).Scatter(scope.data, scope.options);
        scope.$emit("create", chart);
        return chart;
    }

    var lastLegend = null;
    function setLegend(canvas, chart) {
        var legendElem = canvas.parentNode.querySelector(".legend");
        if (legendElem) {
            var legend = chart.generateLegend();
            if (lastLegend !== legend) {
                legendElem.innerHTML =legend;
                lastLegend = legend;
            }
        }
    }

    function isEmpty(value) {
        return !value ||
            (Array.isArray(value) && !value.length) ||
            (typeof value === "object" && !Object.keys(value).length);
    }

})(angular, Chart);
