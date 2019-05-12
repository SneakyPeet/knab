(ns y.server
  (:require [y.core :as core]
            [java-time :as time]
            [hiccup.core :as hiccup]
            [org.httpkit.server :as http]
            [cheshire.core :as json]))


(defn- money [n]
  (-> n
      (/ 1000)
      int))


(defn- spend-chart-data [d]
  (let [total-budgeted (money (:total-budgeted d))
        budget-per-day (:budget-per-day d)
        available-per-day (:available-per-day d)
        days-in-month (:days-in-month d)
        all-days (range 1 (inc days-in-month))
        all-days-data (->> all-days
                           (map (fn [d]
                                  {:x d :y (- total-budgeted (money (* budget-per-day (dec d))))})))
        spend-per-day (->> d
                           :transactions
                           (map (juxt #(:day-of-month (time/as-map (time/local-date "yyyy-MM-dd" (:date %)))) :amount))
                           (group-by first)
                           (map (fn [[day amounts]]
                                  [day (->> amounts (map last) (reduce +))]))
                           (sort-by first))
        actual (loop [total (:total-budgeted d)
                      items spend-per-day
                      result []]
                 (if (empty? items)
                   result
                   (let [[day amount] (first items)
                         new-total (+ total amount)]
                     (recur
                      new-total
                      (rest items)
                      (conj result {:x day :y (money new-total)})))))
        ]
    {:type "line"
     :data {:labels (map str all-days)
            :datasets
            [{:label "budget"
              :fill false
              :pointBackgroundColor	"transparent"
              :pointBorderColor "transparent"
              :data all-days-data}
             {:label "actual"
              :borderColor (if (>= available-per-day budget-per-day) "lightgreen" "red")
              :fill false
              :pointBackgroundColor	"transparent"
              :pointBorderColor "transparent"
              :data actual}]}
     :options {:legend {:display false}
               :scales {:yAxes [{:display false} {:display false}]
                        :xAxes [{:display false :ticks {:display false}}
                                {:display false :ticks {:display false}}]}}}))


(defn main-page [d]
  (let [content
        [:div.columns
         [:div.column.is-narrow
          [:h1.title.has-text-right "Budget"]
          [:table.table.is-fullwidth
           [:tbody
            [:tr [:th "Budget"] [:td.has-text-right (money (:total-budgeted d))]]
            [:tr [:th "Spent"] [:td.has-text-right (money (:total-spent d))]]
            [:tr
             [:th {:style "border-top: solid 2px black"} "Left"]
             [:td.has-text-right
              {:style "border-top: solid 2px black"}
              (money (:total-left d))]]
            ]]
          [:canvas {:id "spend" :height "80"}]
          (when-not (zero? (:days-ahead-of-budget d))
            [:div.has-text-danger {:style "text-align: center; margin-top: 5px;"}
             (:days-ahead-of-budget d) " days ahead of budget!"])]
         [:div.column.is-narrow
          [:h1.title.has-text-right "Available"]
          [:table.table.is-fullwidth
           [:thead
            [:tr [:th] [:th "Budgeted"] [:th "Actual"]]]
           [:tbody

            [:tr
             [:th "Available Per Day"]
             [:td.has-text-right (money (:budget-per-day d))]
             [:td.has-text-right (money (:available-per-day d))]]
            [:tr
             [:th "Available Per Week"]
             [:td.has-text-right (money (* 7 (:budget-per-day d)))]
             [:td.has-text-right (money (* 7 (:available-per-day d)))]]
            ]]]
         [:div.column.is-narrow
          [:h1.title.has-text-right "Category Spend"]
          [:table.table.is-fullwidth
           [:thead
            [:tr [:th "Category"] [:th "Spent"]]]
           [:tbody
            (->> (:categories d)
                 (filter #(neg? (:activity %)))
                 (sort-by :activity)
                 (map
                  (fn [{:keys [name activity]}]
                    [:tr [:td name] [:td.has-text-right (money (Math/abs activity))]])))]]]
         [:div.column
          [:h1.title.has-text-right "Transactions"]
          [:div {:style "overflow-x: scroll; width: 100%"}
           [:table.table.is-fullwidth
            [:thead
             [:tr [:th "When"] [:th "What"] [:th "Where"] [:th "Who"]]]
            [:tbody
             (->> (:transactions d)
                  (sort-by :date)
                  reverse
                  (map
                   (fn [{:keys [date category payee_name amount] :as t}]
                     [:tr
                      [:td (time/as (time/local-date date) :day-of-month)]
                      [:td.has-text-right (money amount)]
                      [:td (:name category)]
                      [:td payee_name]])))]]]]
         [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.8.0/Chart.bundle.min.js"}]
         [:script
          (str
           "var data = JSON.parse('" (json/generate-string (spend-chart-data d)) "');"
           "var ctx = document.getElementById('spend');"
           "var spendChart = new Chart(ctx, data);")]]]
    (hiccup/html
     [:html
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.4/css/bulma.min.css"}]]
      [:body
       [:section.section
        content]
       ]])
    ))


(defn build-page []
  (let [data (core/process-data (core/all-data (core/get-config)))
        content (main-page data)]
    content))


(defn app [req]
  (try
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (build-page)}
    (catch Exception ex
      {:status 500
       :body (ex-cause ex)})))


(defonce ^:private *server* (atom nil))


(defn stop-server []
  (when-not (nil? @*server*)
    (@*server* :timeout 100)
    (reset! *server* nil)))


(defn start-server []
  (reset! *server* (http/run-server #'app {:port 8080})))


(defn -main [& args]
  (start-server))
