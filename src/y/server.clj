(ns y.server
  (:require [y.core :as core]
            [java-time :as time]
            [hiccup.core :as hiccup]
            [org.httpkit.server :as http]
            [cheshire.core :as json]
            [clojure.string :as s]))


(defn main-page [d]
  (let [indicator-class "show-trouble"
        content
        [:div.columns
         [:div.column.is-narrow
          [:h1 {:id "available" :style "font-size: 70px; text-align: center;"}]
          [:canvas {:id "spend-trend" :height "80"}]
          [:canvas {:id "spend-daily" :height "30"}]
          [:h1.title.has-text-right {:style "margin-top: 30px"}"Budget"]
          [:table.table.is-fullwidth
           [:tbody
            [:tr [:th "Budget"] [:td.has-text-right (:total-budgeted d)]]
            [:tr [:th "Spent"] [:td.has-text-right (:total-spent d)]]
            [:tr
             [:th {:style "border-top: solid 2px black" :class indicator-class} "Left"]
             [:td.has-text-right
              {:style "border-top: solid 2px black"
               :class indicator-class}
               (:total-left d)]]
            ]]
          [:div {:style "text-align: center; margin-top: 5px;" :class indicator-class}
           (:days-ahead-of-budget d)
           [:span {:id "ahead-of-budget"}]
           " days ahead of budget"]]
         [:div.column.is-narrow
          [:h1.title.has-text-right "Available"]
          [:table.table.is-fullwidth
           [:thead
            [:tr [:th] [:th "Budgeted"] [:th {:class indicator-class} "Actual"]]]

           [:tbody

            [:tr
             [:th "Available Per Day"]
             [:td.has-text-right (:budget-per-day d)]
             [:td.has-text-right {:class indicator-class :id "available-per-day"} ]]
            [:tr
             [:th "Available Per Week"]
             [:td.has-text-right (* 7 (:budget-per-day d))]
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
                    [:tr [:td name] [:td.has-text-right (core/money (Math/abs activity))]])))]]]
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
                      [:td.has-text-right (core/money amount)]
                      [:td (:name category)]
                      [:td payee_name]])))]]]]
         [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.8.0/Chart.bundle.min.js"}]
         [:script {:src "//cdnjs.cloudflare.com/ajax/libs/ramda/0.25.0/ramda.min.js"}]
         [:script (str "var DATA = JSON.parse('" (json/generate-string (:usage-per-day d)) "');")]
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



(defn build-page [] (main-page (core/fetch-and-process-data)))


;;;; DEV SERVER

(defonce ^:private *data (atom nil))


(defn- reset-data []
  (reset! *data (core/fetch-and-process-data)))


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
