(ns ^:figwheel-always reagent-table.dev
    (:require [reagent.core :as r]
              [reagent-table.core :as rt]
              [goog.events :as events]))

(enable-console-print!)

;; generate some dummy data
(def table-data {:headers ["Row 1" "Row 2" "Row 3" "Row 4"]
                         ;:rows [["Row 1" "Row 2" "Row 3" "Row 4"]

                 :rows (take 20 (partition 4 (repeatedly (fn [] [:span (rand-nth (range 5000000000000000))]))))})


(r/render-component 
 [:div.container {:style {:font-size 16 :margin-top 10}}
  [:div.panel.panel-default
   [:div.panel-body
    [rt/reagent-table table-data {:table 
                                  {:class "table table-hover table-striped table-bordered table-transition"}
                                  :caption [:caption "Test caption"]
                                  :rows-selection {:ul
                                                   {:li {:class "btn"}}}}]]]]
 (. js/document (getElementById "app")))
