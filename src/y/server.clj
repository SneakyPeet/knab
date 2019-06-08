(ns y.server
  (:require [y.core :as core]
            [java-time :as time]
            [hiccup.core :as hiccup]
            [org.httpkit.server :as http]
            [cheshire.core :as json]
            [clojure.string :as s]))


(defn- money [n]
  (if n
    (-> n
        (/ 1000)
        int)
    0))

(defn- usage-per-day [d]
  (let [total-budgeted (money (:total-budgeted d))
        budget-per-day (money (:budget-per-day d))
        available-per-day (money (:available-per-day d))
        days-in-month (:days-in-month d)
        all-days (range 1 (inc days-in-month))
        spend-per-day (->> d
                           :transactions
                           (map (juxt #(:day-of-month (time/as-map (time/local-date "yyyy-MM-dd" (:date %)))) :amount))
                           (group-by first)
                           (map (fn [[day amounts]]
                                  [day (->> amounts (map last) (reduce +) money)]))
                           (into {}))]
    (loop [spent 0
           days all-days
           result []]
      (if (empty? days)
        result
        (let [day (first days)
              spent-on-day (get spend-per-day day 0)
              total-spent (+ spent-on-day spent)
              spend-in-month (+ total-budgeted total-spent)
              available-at-date (* day budget-per-day)
              used-at-date (+ available-at-date total-spent)
              trend (- total-budgeted available-at-date)
              dailybudget (int (/ spend-in-month  (- days-in-month (dec day)) ))
              day-data {:day day
                        :spendonday spent-on-day
                        :spendinmonth spend-in-month
                        :trend trend
                        :availableonday used-at-date
                        :trouble (neg? used-at-date)
                        :dailybudget dailybudget
                        :weeklybudget (* dailybudget 7)
                        :daysahead (Math/abs (min 0 (int (Math/floor (/ used-at-date budget-per-day)))))}]
          (recur
           total-spent
           (rest days)
           (conj result day-data)))))))


(defn main-page [d]
  (let [indicator-class "show-trouble"
        content
        [:div.columns
         [:div.column.is-narrow
          [:h1 {:id "available" :style "font-size: 70px; text-align: center;"}]
          [:canvas {:id "spend-trend" :height "80"}]
          [:h1.title.has-text-right {:style "margin-top: 30px"}"Budget"]
          [:table.table.is-fullwidth
           [:tbody
            [:tr [:th "Budget"] [:td.has-text-right (money (:total-budgeted d))]]
            [:tr [:th "Spent"] [:td.has-text-right (money (:total-spent d))]]
            [:tr
             [:th {:style "border-top: solid 2px black" :class indicator-class} "Left"]
             [:td.has-text-right
              {:style "border-top: solid 2px black"
               :class indicator-class}
              (money (:total-left d))]]
            ]]
          (when-not (zero? (:days-ahead-of-budget d))
            [:div {:style "text-align: center; margin-top: 5px;" :class indicator-class}
             (:days-ahead-of-budget d)
             " days ahead of budget ("
             [:span {:id "ahead-of-budget"}]
             ")"])]
         [:div.column.is-narrow
          [:h1.title.has-text-right "Available"]
          [:table.table.is-fullwidth
           [:thead
            [:tr [:th] [:th "Budgeted"] [:th {:class indicator-class} "Actual"]]]

           [:tbody

            [:tr
             [:th "Available Per Day"]
             [:td.has-text-right (money (:budget-per-day d))]
             [:td.has-text-right {:class indicator-class :id "available-per-day"} ]]
            [:tr
             [:th "Available Per Week"]
             [:td.has-text-right (money (* 7 (:budget-per-day d)))]
             [:td.has-text-right {:class indicator-class :id "available-per-week"} ]]
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
         [:script {:src "//cdnjs.cloudflare.com/ajax/libs/ramda/0.25.0/ramda.min.js"}]
         [:script (str "var DATA = JSON.parse('" (json/generate-string (usage-per-day d)) "');")]
         [:script {:src "/ynab.js"}]]]
    (hiccup/html
     [:html
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
       [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/bulma/0.7.4/css/bulma.min.css"}]]
      [:body
       [:section.section
        content]
       ]])))


(defn- fetch-data []
  (core/process-data (core/all-data (core/get-config))))


(defn build-page [] (main-page (fetch-data)))


(defonce ^:private *data (atom nil))


(defn- reset-data []
  (reset! *data (fetch-data)))


(defn app [req]
  (try
    (let [file (:uri req)
          content-type (cond
                         (s/ends-with? file ".js") "text/javascript"
                         :else "text/html")
          body (if (= "/" file)
                 (main-page @*data)
                 (slurp (str "public" file)))]
      {:status 200
       :headers {"Content-Type" content-type}
       :body body})
    (catch Exception ex
      {:status 500
       :body (ex-cause ex)})))


(defonce ^:private *server (atom nil))


(defn stop-server []
  (when-not (nil? @*server)
    (@*server :timeout 100)
    (reset! *server nil)))


(defn start-server []
  (reset! *server (http/run-server #'app {:port 8080})))


(comment

  (do
    (reset-data)
    (start-server))

  (stop-server)
  )
