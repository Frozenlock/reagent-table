(ns reagent-table.core
    (:require [reagent.core :as r]
              [goog.events :as events])
    (:import [goog.events EventType]))

;;; Is the code horrible? Yup!
;;; Does it work? Yup!
;;; Do I have time to clean it up? Not really...



(defn drag-move-fn [on-drag]
  (fn [evt]
    (.preventDefault evt) ;; otherwise we select text while resizing
    (on-drag (.-clientX evt) (.-clientY evt))))

(defn drag-end-fn [drag-move drag-end]
  (fn [evt]
    (events/unlisten js/window EventType.MOUSEMOVE drag-move)
    (events/unlisten js/window EventType.MOUSEUP @drag-end)))

(defn dragging [on-drag]
  (let [drag-move (drag-move-fn on-drag)
        drag-end-atom (atom nil)
        drag-end (drag-end-fn drag-move drag-end-atom)]
    (reset! drag-end-atom drag-end)
    (events/listen js/window EventType.MOUSEMOVE drag-move)
    (events/listen js/window EventType.MOUSEUP drag-end)))
  


(defn recursive-merge
  "Recursively merge hash maps."
  [a b]
  (if (and (map? a) (map? b))
    (merge-with recursive-merge a b)
    b))


(defn sort-fn
  [rows column ascending?]
  (let [sorted (sort-by (fn [r] 
                          (let [cell (nth r column)]
                            (get (meta cell) :data cell))) rows)]
    (if ascending? sorted (rseq sorted))))

(def default-configs {:table
                      {:style {:width nil}
                       :thead
                       {:tr
                        {
                         :th
                         {:style {;:transition "all 0.2s ease-in-out;"
                                  ;; :-moz-user-select "none"
                                  ;; :-webkit-user-select "none"
                                  ;; :-ms-user-select "none"
                                  }}}}}})

(defn reorder-column! [source-atom c1 c2]
  (let [swap-vec (fn [v i1 i2]
                   (let [v (vec v)]
                     (assoc v i2 (nth v i1) i1 (nth v i2))))
        source @source-atom
        updated-source (-> source
                           (update-in [:headers] swap-vec c1 c2)
                           (update-in [:rows] (fn [s] (mapv #(swap-vec % c1 c2) s))))]
    (reset! source-atom updated-source)))
    


(defn resize-widget [cell-container]
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


(defn header-cell-fn [i n configs source-atom state-atom]
  (let [
        state @state-atom
        col-hidden (:col-hidden state)
        col-state-a (r/cursor state-atom [:col-state n])
        sort-click-fn (fn []
                        (let [sorting (-> (swap! state-atom update-in [:sorting]
                                                 #(if-not (= [n :asc] %)
                                                    [n :asc]
                                                    [n :desc]))
                                          (get-in [:sorting 1]))]
                          (swap! source-atom update-in [:rows]
                                 sort-fn n (= sorting :asc))))]
    [:th
     (recursive-merge 
      (get configs :th)
      {;:width (str (get @col-state-a :width) "px") ;; <--------
       :draggable true
       :on-drag-start #(do (doto (.-dataTransfer %)
                             (.setData "text/plain" "")) ;; for Firefox
                           (swap! state-atom assoc :col-reordering true))
       :on-drag-over #(swap! state-atom assoc :col-hover n)
       :on-drag-end #(let [col-hover (:col-hover @state-atom)
                           col-sorting (first (get @state-atom :sorting))]
                       (when-not (= n col-hover) ;; if dropped on another column
                         (reorder-column! source-atom n col-hover)
                         (when (some #{col-sorting} [n col-hover])
                           ;; if any of them is currently sorted
                           (swap! state-atom update-in
                                  [:sorting]
                                  (fn [[col-n sort-type]]
                                    [(if (= col-n n) col-hover n) sort-type]))))
                       (swap! state-atom assoc 
                              :col-hover nil
                              :col-reordering nil))
       :style (merge {:position "relative"
                      :cursor "move"
                      :display (when (get col-hidden n) "none")}
                     (when (and (:col-reordering state)
                                (= n (:col-hover state)))
                       {:border-right "6px solid #3366CC"}))})
     [:span {:style {:padding-right 50}} i]
     [:span {:style {:position "absolute"
                     :text-align "center"
                     :height "1.5em"
                     :width "1.5em"
                     :right "15px"
                     :cursor "pointer"}
             :on-click sort-click-fn}
      (condp = (get state :sorting)
        [n :asc] " ▲" 
        [n :desc] " ▼"
        ;; and to occupy the same space...
        [:span {:style {:opacity "0.3"}}
         " ▼"])]
     [resize-widget (r/current-component)]]))


(defn header-row-fn [items configs source-atom state-atom]
  (let [state @state-atom]
    [:tr
     (for [[n i] (map-indexed (fn [& args] args) items)]
       ^{:key n}
       [header-cell-fn i n configs source-atom state-atom])]))


(defn row-fn [item state-atom]
  (let [comp (r/current-component)
        state @state-atom
        col-hidden (:col-hidden state)]
  [:tr ;; {:draggable true
       ;;  :on-drag-start #(do (doto (.-dataTransfer %)
       ;;                        (.setData "text/plain" "") ;; for Firefox
       ;;                        ))}
   (doall
    (map-indexed (fn [n i]
                   ^{:key n}
                   [:td {:on-drag-over #(swap! state-atom assoc :col-hover n)
                         :style {:border-right (when (and (:col-reordering state)
                                                          (= n (:col-hover state)))
                                                 "2px solid #3366CC")
                                 :display (when (get col-hidden n) "none")}}
                    i]) item))]))
  
(defn rows-fn [rows state-atom]
  (let [comp (r/current-component)]
    (doall (map-indexed
            (fn [n i]
              ^{:key n}
              [row-fn i state-atom]) rows))))



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
         state-atom (r/atom {})] ;; a place for the table local state
     (assert (let [data @data-atom]
               (and (:headers data)
                    (:rows data)))
             "Missing :headers or :rows in the provided data.")
                   
     (fn []
       (let [data @data-atom]
         [:div
          [:style (str ".reagent-table * table {table-layout:fixed;}"
                       ".reagent-table * td { max-width: 3px;"
                       "overflow: hidden;text-overflow: ellipsis;white-space: nowrap;}")]
          (when-let [selector-config (:rows-selection table-configs)]
            ;(js/console.log (str selector-config))
            [rows-selector data-atom state-atom selector-config])
          [:table.reagent-table (:table table-configs)
           (when-let [caption (:caption table-configs)]
             caption)
           [:thead (:thead table-configs)
            (header-row-fn (:headers data) 
                           (get-in table-configs [:table :thead :tr]) 
                           data-atom 
                           state-atom)]
           [:tbody (:tbody table-configs)
            (rows-fn (:rows data) state-atom)]]])))))

