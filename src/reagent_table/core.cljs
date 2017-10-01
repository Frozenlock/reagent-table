(ns reagent-table.core
    (:require [reagent.core :as r]
              [goog.events :as events])
    (:import [goog.events EventType]))

;;; Is the code horrible? Yup!
;;; Does it work? Yup!
;;; Do I have time to clean it up? Not really...

(defn- drag-move-fn [on-drag]
  (fn [evt]
    (.preventDefault evt) ;; otherwise we select text while resizing
    (on-drag (.-clientX evt) (.-clientY evt))))

(defn- drag-end-fn [drag-move drag-end]
  (fn [evt]
    (events/unlisten js/window EventType.MOUSEMOVE drag-move)
    (events/unlisten js/window EventType.MOUSEUP @drag-end)))

(defn- dragging [on-drag]
  (let [drag-move (drag-move-fn on-drag)
        drag-end-atom (atom nil)
        drag-end (drag-end-fn drag-move drag-end-atom)]
    (reset! drag-end-atom drag-end)
    (events/listen js/window EventType.MOUSEMOVE drag-move)
    (events/listen js/window EventType.MOUSEUP drag-end)))
  


(defn- recursive-merge
  "Recursively merge hash maps."
  [a b]
  (if (and (map? a) (map? b))
    (merge-with recursive-merge a b)
    b))

(defn- column-index-to-model
  "Convert the given column in view coordinates to
  model coordinates."
  [state-atom view-col]
  (-> @state-atom
      :col-index-to-model
      (nth view-col)))

(defn- reorder-column-index-to-model!
  "Maintain the column-index-to-model mapping after
  view reordering. The arguments are the drag and drop
  columns, in view coordinates."
  [drag-col drop-col state-atom]
  (let [cur          (:col-index-to-model @state-atom)
        lower-bound  (min drag-col drop-col)
        upper-bound  (max drag-col drop-col)
        direction    (if (< drag-col drop-col) :right :left)
        moving-model (column-index-to-model state-atom drag-col)]
    ;(.log js/console (str "drag-col: " drag-col))
    ;(.log js/console (str "drop-col: " drop-col))
    (swap! state-atom
           assoc :col-index-to-model
                 (into []
                       (map-indexed
                         (fn [view-col model-col]
                           (cond (= view-col drop-col)
                                 (cur drag-col)

                                 (or (< view-col lower-bound)
                                     (> view-col upper-bound))
                                 (cur view-col)

                                 (and (>= view-col lower-bound)
                                      (= direction :right))
                                 (cur (inc view-col))

                                 (and (<= view-col upper-bound)
                                      (= direction :left))
                                 (cur (dec view-col))))
                         cur)))))

(def default-configs {:table
                      {:style {:width nil}}})
                       ;:thead {:tr {:th {:style
                       ;                  {;:transition "all 0.2s ease-in-out;"
                       ;                   ;; :-moz-user-select "none"
                       ;                   ;; :-webkit-user-select "none"
                       ;                   ;; :-ms-user-select "none"
                       ;                   }}}}}})

(defn- resize-widget [cell-container]
  [:span {:style {:display "inline-block"
                  :width "8"
                  :position "absolute"
                  :cursor "ew-resize"
                  :height "100%"
                  :top 0
                  :right 0
                  ;:background-color "black" ;; for debug
                  }
          :on-click #(.stopPropagation %)
          :on-mouse-down #(let [cell-node (r/dom-node cell-container)
                                init-x (.-clientX %)
                                init-width (.-clientWidth cell-node)]
                            (dragging
                             (fn [x _]
                               (aset cell-node "width" (- init-width (- init-x x)))))
                            (.preventDefault %))}])

(defn- update-sort-columns!
  "Maintain multiple sort columns each with individual directions. The
  column numbers are in model coordinates.

  A column is initially set to ascending order and toggled thereafter.
  If a column is not present in list it is appended or becomes the only
  element when 'append' is false."
  [model-col state-atom append]
  (let [sorting (:sorting @state-atom)]
    (swap! state-atom
           assoc :sorting
           (if-not append
             [[model-col (if (= (first sorting) [model-col :asc]) :desc :asc)]]
             (loop [sorting sorting
                    found false
                    result []]
               (let [column (first sorting)
                     this-col (first column)
                     this-dir (second column)]
                 (if column
                   (if (= model-col this-col)
                     (recur (rest sorting)
                            true
                            (conj result [model-col (if (= this-dir :asc) :desc :asc)]))
                     (recur (rest sorting)
                            found
                            (conj result column)))
                   (if found
                     result
                     (conj result [model-col :asc])))))))))

(defn- is-sorting
  "Return the sort direction for the specified column number, nil
  if the column is not currently sorted, or :none if the column is not
  sortable at all. Column number must be in model coordinates."
  [sorting render-info model-col]
  (if (false? (:sortable render-info))
    :none
    (-> (filter #(= (first %) model-col)
                sorting)
        first
        second)))

(defn header-cell-fn [render-info
                      view-col
                      model-col
                      configs
                      state-atom
                      data-atom]
  (let [state         @state-atom
        col-hidden    (:col-hidden state)
        {:keys [draggable]} state
        sort-fn       (:sort configs)
        column-model  (:column-model configs)
        sortable      (not (false? (:sortable render-info)))
        sort-click-fn (fn [append]
                        (when sort-fn
                          (swap! data-atom assoc :rows (sort-fn (:rows @data-atom)
                                   column-model
                                   (:sorting (update-sort-columns! model-col state-atom append)))))
                        (.log js/console (str "append: " append))
                        (.log js/console (str "sorting: " (:sorting @state-atom)))
                        )]
    [:th
     (recursive-merge
      (:th configs)
      {;:width (str (get @col-state-a :width) "px") ;; <--------
       :draggable draggable
       :on-drag-start #(do (doto (.-dataTransfer %)
                             (.setData "text/plain" "")) ;; for Firefox
                           (swap! state-atom assoc :col-reordering true))
       :on-drag-over #(swap! state-atom assoc :col-hover view-col)
       :on-drag-end #(let [col-hover (:col-hover @state-atom)
                           col-sorting (first (get @state-atom :sorting))]
                       (when-not (= view-col col-hover) ;; if dropped on another column
                         (reorder-column-index-to-model! view-col col-hover state-atom)
                         ;(reorder-column! data-atom view-col col-hover)
                         (comment (when (some #{col-sorting} [view-col col-hover])
                           ;; if any of them is currently sorted
                           (swap! state-atom update-in
                                  [:sorting]
                                  (fn [[col-n sort-type]]
                                    [(if (= col-n view-col) col-hover view-col) sort-type])))))
                       (swap! state-atom assoc
                              :col-hover nil
                              :col-reordering nil))
       :style (merge {:position "relative"
                      :cursor (if draggable "move" nil)
                      :display (when (get col-hidden view-col) "none")}
                     (when (and (:col-reordering state)
                                (= view-col (:col-hover state)))
                       {:border-right "6px solid #3366CC"}))})
     [:span {:style {:padding-right 50}} (:header render-info)]
     (when (and sort-fn sortable)
       [:span {:style {:position "absolute"
                       :text-align "center"
                       :height "1.5em"
                       :width "1.5em"
                       :right "15px"
                       :cursor "pointer"}
               :on-click #(sort-click-fn (.-ctrlKey %))}
        (condp = (is-sorting (:sorting state) render-info model-col)
          :asc " ▲"
          :desc " ▼"
          :none nil
          ;; sortable but not participating
          [:span {:style {:opacity "0.3"}}
           " ▼"])])
     [resize-widget (r/current-component)]]))


(defn header-row-fn [column-model configs data-atom state-atom]
  (let [state @state-atom]
    [:tr
     (doall (map-indexed (fn [view-col _]
                           (let [model-col (column-index-to-model state-atom view-col)
                                 render-info (column-model model-col)]
                              ^{:key (or (:key render-info) model-col)}
                              [header-cell-fn render-info view-col model-col configs state-atom data-atom]))
                         column-model))]))


(defn row-fn [row row-num row-key-fn state-atom config]
  (let [state @state-atom
        col-hidden (:col-hidden state)
        col-key-fn (:col-key config (fn [row row-num col-num] col-num))
        col-model  (:column-model config)
        cell-fn    (:cell config (fn [cell row row-num col-num] cell))]
    ^{:key (row-key-fn row row-num)}
    [:tr
     (doall
       (map-indexed (fn [view-col _]
                      (let [model-col (column-index-to-model state-atom view-col)]
                        ^{:key (col-key-fn row row-num model-col)}
                        [:td
                         {:style  {:border-right (when (and (:col-reordering state)
                                                            (= view-col (:col-hover state)))
                                                             "2px solid #3366CC")
                                   :display      (when (get col-hidden view-col) "none")}}
                         (cell-fn (col-model model-col) row row-num model-col)]))
                    (or
                      col-model
                      row)))]))
  
(defn rows-fn [rows state-atom config]
  (let [row-key-fn (:row-key config (fn [row row-num] row-num))]
  (doall (map-indexed
           (fn [row-num row]
             (row-fn row row-num row-key-fn state-atom config))
           rows))))



(defn rows-selector [data-atom state-atom configs]
  (let [headers (:headers @data-atom)
        hidden-rows (r/cursor state-atom [:col-hidden])]
    [:ul (:ul configs)
     (doall
      (for [[col-n i] (map-indexed #(vector %1 %2) headers) 
            :let [hidden-a (r/cursor hidden-rows [col-n])
                  li-config (get-in configs [:ul :li])]]
        ^{:key col-n}
        [:li (recursive-merge
              {:style {:margin 8
                       :cursor "pointer"}
               :on-click #(do (swap! hidden-a not) nil)}
              li-config)
         i " "(if @hidden-a "☐" "☑")]))]))

(defn- init-column-index
  "Set up in the initial column-index-to-model numbers"
  [headers]
  (into [] (map-indexed (fn [idx _] idx) headers)))

(defn reagent-table
  "Optional properties map include :table :thead and :tbody.
  For example:
  (reagent-table ... {:table {:class \"table-striped\"}})"
  ([data-or-data-atom] (reagent-table data-or-data-atom {}))
  ([data-or-data-atom table-configs]
   (let [table-configs (recursive-merge default-configs table-configs)
         data-atom (if-not (satisfies? IAtom data-or-data-atom)
                     (r/atom data-or-data-atom)
                     data-or-data-atom)                    
         state-atom (or (:table-state table-configs) (r/atom {})) ;; a place for the table local state
         {:keys [headers rows]} @data-atom]
     (assert (and headers rows)
             "Missing :headers or :rows in the provided data.")
     ; TODO cursors or something to separate headers from rows so we can reinitialise column indexes only when headers change
     (swap! state-atom #(assoc % :col-index-to-model (init-column-index (:rows-selection table-configs))))
     (fn []
       (let [data @data-atom]
         [:div
          [:style (str ".reagent-table * table {table-layout:fixed;}"
                       ".reagent-table * td { max-width: 3px;"
                       "overflow: hidden;text-overflow: ellipsis;white-space: nowrap;}")]
          (when-let [selector-config (:rows-selection table-configs)]
            [rows-selector data-atom state-atom selector-config])
          [:table.reagent-table (:table table-configs)
           (when-let [caption (:caption table-configs)]
             caption)
           [:thead (:thead table-configs)
            (header-row-fn (:column-model table-configs)
                           table-configs
                           data-atom 
                           state-atom)]
           [:tbody (:tbody table-configs)
            (rows-fn (:rows data) state-atom table-configs)]]])))))

