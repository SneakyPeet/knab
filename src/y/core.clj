(ns y.core
  (:require [y.api :as api]
            [clj-http.client :as http]
            [java-time :as time]))


;;;; CONFIG

(defn- get-config
  ([] (get-config "config.edn"))
  ([path]
   (let [{:keys [budget-id token] :as config} (read-string (slurp path))
         client (api/client token budget-id http/request)]
     (assoc config :client client))))


;;;; HELPERS

(defn money [n]
  (if n
    (-> n
        (/ 1000)
        int)
    0))


(defn- current-month [month-data]
  (get-in month-data [:month :month]))


;;;; FETCH

(defn- categories [config month-data]
  (let [{:keys [category-group-id uncategorized-group-id ]} config
        category-groups (->> month-data
                             :month
                             :categories
                             (group-by :category_group_id))]
    (->>
     (conj (get category-groups category-group-id)
           (->> (get category-groups uncategorized-group-id)
                (filter #(= "Uncategorized" (:name %)))
                first))
     (filter #(false? (:deleted %)))
     (filter #(false? (:hidden %)))
     (map #(select-keys % [:category_group_id :name :budgeted :activity :balance :id])))))


(defn- transactions [config month-data categories]
  (let [client (:client config)
        since (current-month month-data)]
    (->> (for [category categories]
           (->> (api/->data
                 (api/parse-response
                  (client (api/transactions-for-category (:id category) since))))
                :transactions
                (map #(assoc % :category category))))
         (reduce into)
         (filter #(false? (:deleted %)))
         (map #(select-keys % [:category :amount :date :payee_name])))))


(defn- all-data [config]
  (let [{:keys [client]} config
        month (api/->data (api/parse-response (client (api/current-month))))
        categories (categories config month)
        transactions (transactions config month categories)]
    {:month (dissoc (:month month) :categories)
     :categories categories
     :transactions transactions}))


;;;; PROCESS

(defn- usage-per-day [d]
  (let [total-budgeted (:total-budgeted d)
        budget-per-day (:budget-per-day d)
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


(defn- process-data [data]
  (let [{:keys [categories transactions]} data
        total-budgeted (->> categories
                            (map :budgeted)
                            (reduce +))
        total-spent (->> transactions
                         (map :amount)
                         (reduce +))
        total-left (+ total-budgeted total-spent)
        start-of-month (time/local-date "yyyy-MM-dd" (current-month data))
        days-in-month (time/as (time/minus (time/plus start-of-month (time/months 1)) (time/days 1)) :day-of-month)
        current-day (time/as (time/local-date) :day-of-month)
        days-left (- days-in-month (dec current-day))
        budget-per-day (/ total-budgeted days-in-month)
        available-per-day (/ total-left days-left)
        base-data
        (assoc data
               :total-budgeted (money total-budgeted)
               :total-spent (money (Math/abs total-spent))
               :total-left (money total-left)
               :days-in-month days-in-month
               :budget-per-day (money budget-per-day)
               :available-per-day available-per-day)]
    (assoc base-data :usage-per-day (usage-per-day base-data))))


;;;; MAIN
(defn fetch-and-process-data []
  (process-data (all-data (get-config))))
