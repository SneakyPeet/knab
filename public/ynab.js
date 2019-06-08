console.table(DATA);

const troubleClassPlaceholder = "show-trouble";
const aheadOfBudgetId = "ahead-of-budget";
const availablePerDayId = "available-per-day";
const availablePerWeekId = "available-per-week";
const availableTodayId = "available";

const currentDay = new Date().getDate();
const currentDayData = DATA[currentDay - 1];

const troubleClass = currentDayData.trouble ? "has-text-danger" : "has-text-success";

function setTroubleClass(el) {
  el.classList.add(troubleClass);
}

function setDaysAheadTextIfNeeded() {
  const el = document.getElementById(aheadOfBudgetId);
  if (currentDayData.trouble) {
    el.innerHTML = currentDayData.daysahead;
  } else {
    el.parentNode.style.display = "none";
  }
}

// Side effects
R.map(setTroubleClass, document.getElementsByClassName(troubleClassPlaceholder));
setDaysAheadTextIfNeeded();
document.getElementById(availablePerDayId).innerHTML = currentDayData.dailybudget;
document.getElementById(availablePerWeekId).innerHTML = currentDayData.weeklybudget;
document.getElementById(availableTodayId).innerHTML = currentDayData.availableonday;

//charts
const trendChartId = "spend-trend";
const trendChartData = {
  type: "line",
  data: {
    labels: R.map(R.prop("day"), DATA),
    datasets: [{
      fill: false,
      pointBackgroundColor:	"transparent",
      pointBorderColor: "transparent",
      data: R.map(R.applySpec({x: R.prop("day"), y: R.prop("trend")}), DATA)
    }, {
      label: "spend",
      fill: false,
      pointBackgroundColor:	"transparent",
      pointBorderColor: "transparent",
      borderColor: currentDayData.trouble ? "red" : "lightgreen",
      data: R.pipe(
        R.filter(R.compose(R.gte(currentDay), R.prop("day"))),
        R.map(R.applySpec({x: R.prop("day"), y: R.prop("spendinmonth")}))
      )(DATA)
    }]
  },
  options: {
    legend: {display: false},
    scales: {
      yAxes: [{display: false}, {display: false}],
      xAxes: [{display: false, ticks: {display: false}}, {display: false, ticks: {display: false}}]
    }
  }
};

new Chart(document.getElementById(trendChartId), trendChartData);
