module Prelude.Luna (module Prelude.Luna, module X) where

import           Data.Reprx        as X
import           Prologue          as X hiding (Cons, Getter, Repr, Setter, cons, read, repr, (#), universe)
import           Data.Construction as X
import           Control.Monad     as X (forM, forM_, filterM)

import           Control.Monad.Except (throwError, MonadError)

infixr 1 <?!>
(<?!>) :: MonadError e m => m (Maybe a) -> e -> m a
a <?!> e = do
    x <- a
    case x of
        Just v  -> return v
        Nothing -> throwError e
