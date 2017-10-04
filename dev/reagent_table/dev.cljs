(ns ^:figwheel-always reagent-table.dev
  (:require [reagent.core :as r :refer [atom]]
            [reagent-table.core :as rt]
            [goog.events :as events]
            [goog.i18n.NumberFormat.Format])
  (:import
    (goog.i18n NumberFormat)
    (goog.i18n.NumberFormat Format)))

(enable-console-print!)

(def nff (NumberFormat. Format/DECIMAL))

(defn format-number
  [num]
  (.format nff (str num)))

;; generate some dummy data
(def table-data (r/atom
                  [{:Animal {:Name    "Lizard"
                             :Colour  "Green"
                             :Skin    "Leathery"
                             :Weight  100
                             :Age     10
                             :Hostile false}}
                   {:Animal {:Name    "Lion"
                             :Colour  "Gold"
                             :Skin    "Furry"
                             :Weight  190000
                             :Age     4
                             :Hostile true}}
                   {:Animal {:Name    "Giraffe"
                             :Colour  "Green"
                             :Skin    "Hairy"
                             :Weight  1200000
                             :Age     8
                             :Hostile false}}
                   {:Animal {:Name    "Cat"
                             :Colour  "Black"
                             :Skin    "Furry"
                             :Weight  5500
                             :Age     6
                             :Hostile false}}
                   {:Animal {:Name    "Capybara"
                             :Colour  "Brown"
                             :Skin    "Hairy"
                             :Weight  45000
                             :Age     12
                             :Hostile false}}
                   {:Animal {:Name    "Bear"
                             :Colour  "Brown"
                             :Skin    "Furry"
                             :Weight  600000
                             :Age     10
                             :Hostile true}}
                   {:Animal {:Name    "Rabbit"
                             :Colour  "White"
                             :Skin    "Furry"
                             :Weight  1000
                             :Age     6
                             :Hostile false}}
                   {:Animal {:Name    "Fish"
                             :Colour  "Gold"
                             :Skin    "Scaly"
                             :Weight  50
                             :Age     5
                             :Hostile false}}
                   {:Animal {:Name    "Hippo"
                             :Colour  "Grey"
                             :Skin    "Leathery"
                             :Weight  1800000
                             :Age     10
                             :Hostile false}}
                   {:Animal {:Name    "Zebra"
                             :Colour  "Black/White"
                             :Skin    "Hairy"
                             :Weight  200000
                             :Age     9
                             :Hostile false}}
                   {:Animal {:Name    "Squirrel"
                             :Colour  "Grey"
                             :Skin    "Furry"
                             :Weight  300
                             :Age     1
                             :Hostile false}}
                   {:Animal {:Name    "Crocodile"
                             :Colour  "Green"
                             :Skin    "Leathery"
                             :Weight  500000
                             :Age     10
                             :Hostile true}}]))

;the column model
(def columns [{:path [:Animal :Name]
               :header "Name"
               :key :Name}  ; convention - use field name for reagent key
              {:path [:Animal :Colour]
               :header "Colour"
               :key :Colour}
              {:path [:Animal :Skin]
               :header "Skin Type"
               :key :Skin}
              {:path [:Animal :Weight]
               :header "Weight"
               :format #(format-number %)
               :attrs (fn [data] {:style {:text-align "right"
                                          :display "block"}})
               :key :Weight}
              {:path [:Animal :Age]
               :header "Age"
               :attrs (fn [data] {:style {:text-align "right"
                                          :display "block"}})
               :key :Age}
              {:path [:Animal :Hostile]
               :header "Hostile?"
               :format #(if % "Stay Away!" "OK to stroke")
               :key :Hostile}])

(defn- row-key-fn
  "Return the reagent row key for the given row"
  [row row-num]
  (get-in row [:Animal :Name]))

(defn- cell-data
  "Resolve the data within a row for a specific column"
  [row cell]
  (let [{:keys [path expr]} cell]
    (or (and path
             (get-in row path))
        (and expr
             (expr row)))))

(defn- cell-fn
"Return the cell hiccup form for rendering.
 - render-info the specific column from :column-model
 - row the current row
 - row-num the row number
 - col-num the column number in model coordinates"
[render-info row row-num col-num]
(let [{:keys [format attrs]
       :or   {format identity
              attrs (fn [_] {})}} render-info
      data    (cell-data row render-info)
      content (format data)
      attrs   (attrs data)]
  [:span
   attrs
   content]))

(defn date?
  "Returns true if the argument is a date, false otherwise."
  [d]
  (instance? js/Date d))

(defn date-as-sortable
  "Returns something that can be used to order dates."
  [d]
  (.getTime d))

(defn compare-vals
  "A comparator that works for the various types found in table structures.
  This is a limited implementation that expects the arguments to be of
  the same type. The :else case is to call compare, which will throw
  if the arguments are not comparable to each other or give undefined
  results otherwise.

  Both arguments can be a vector, in which case they must be of equal
  length and each element is compared in turn."
  [x y]
  (cond
    (and (vector? x)
         (vector? y)
         (= (count x) (count y)))
    (reduce #(let [r (compare (first %2) (second %2))]
               (if (not= r 0)
                 (reduced r)
                 r))
            0
            (map vector x y))

    (or (and (number? x) (number? y))
        (and (string? x) (string? y))
        (and (boolean? x) (boolean? y)))
    (compare x y)

    (and (date? x) (date? y))
    (compare (date-as-sortable x) (date-as-sortable y))

    :else ;; hope for the best... are there any other possiblities?
    (compare x y)))

(defn- sort-fn
  "Generic sort function for tabular data. Sort rows using data resolved from
  the specified columns in the column model."
  [rows column-model sorting]
  (sort (fn [row-x row-y]
          (reduce
            (fn [_ sort]
              (let [column (column-model (first sort))
                    direction (second sort)
                    cell-x (cell-data row-x column)
                    cell-y (cell-data row-y column)
                    compared (if (= direction :asc)
                               (compare-vals cell-x cell-y)
                               (compare-vals cell-y cell-x))]
                (when-not (zero? compared)
                  (reduced compared))
                ))
            0
            sorting))
        rows))

(def table-state (atom {:draggable true}))

(r/render-component 
 [:div.container {:style {:font-size 16 :margin-top 10}}
  ;[:div.panel.panel-default
   ;[:div.panel-body
    [rt/reagent-table table-data {:table {:class "table table-hover table-striped table-bordered table-transition"}
                                  :table-state  table-state
                                  :column-model columns
                                  :row-key      row-key-fn
                                  :render-cell  cell-fn
                                  :sort         sort-fn
                                  :caption [:caption "Test caption"]
                                  :column-selection {:ul
                                                   {:li {:class "btn"}}}
                                  }]]
;]]
 (. js/document (getElementById "app")))
