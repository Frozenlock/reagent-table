Reagent-table
=========

A table component with all the usual features you would expect.

- Sort rows
- Resizable columns
- Re-order columns
- Hide/show columns


<img src="https://raw.githubusercontent.com/Frozenlock/reagent-table/master/reagent-table.gif"
	alt="Reagent-table"/>


Contrarily to most React.js table components, this one use the correct
HTML tags (table, theader, th, tr...) to be compatible with your
favorite CSS. This also means the data can easily be copied from the
table.



## Usage

Add this to your project dependencies:
[![Clojars Project](http://clojars.org/org.clojars.frozenlock/reagent-table/latest-version.svg)](http://clojars.org/org.clojars.frozenlock/reagent-table)

Require Reagent-table in your namespace:
```clj
(ns my-ns
  (:require [reagent-table.core :as rt]))
```

Then, simply use it as a normal component:
```clj
[rt/reagent-table table-data]
```

`table-data` can be raw data, or data in an atom. **If an atom is
provided, all manipulation will change the data directly inside the atom**.

The data must be a map containing at least the following two keys:
- `:headers`
- `:rows`

For example:
```clj
{:headers ["Row 1" "Row 2" "Row 3" "Row 4"]}
```

The cells could also be reagent component:
```clj
{:headers [[:span "Row 1"]
	       [:span "Row 2"]
		   [:span "Row 3"]
		   [:span "Row 4"]]}
```

`:rows` is similar, but should be a collection of rows, instead of a single row like `:headers`.

When using reagent components as cells, you can add the `:data`
metadata to each of them. This is the value that will be used when
sorting the columns.


See `reagent-table.dev` for a simple working example.



