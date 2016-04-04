{-# LANGUAGE UndecidableInstances #-}

module Luna.Syntax.Model.Network.Builder.Layer (
      module Luna.Syntax.Model.Network.Builder.Layer
    , module X
    ) where

import Prelude.Luna

import Luna.Syntax.Model.Network.Builder.Layers.SuccTracking       as X
import Luna.Syntax.Model.Network.Builder.Layers.MembershipTracking as X
import Luna.Syntax.Model.Network.Builder.Layers.TCData             as X

import           Data.Graph.Builders
import           Control.Monad.Event
import           Data.Graph.Model.Pointer.Set         (RefSet)
import           Data.Prop
import           Data.Record
import           Data.Construction
import           Data.Graph.Backend.NEC
import qualified Luna.Syntax.Model.Network.Builder.Type as Type
import qualified Luna.Syntax.Model.Network.Builder.Self as Self
import           Luna.Syntax.Model.Network.Builder.Self (MonadSelfBuilder, self)
import qualified Luna.Syntax.Term.Function               as Func
import           Data.Graph.Builder.Class
import           Luna.Syntax.Model.Layer
import           Data.Graph.Builder.Ref                 as Ref
import           Luna.Syntax.Model.Network.Class
import           Data.Layer_OLD.Cover_OLD
import           Data.Graph




-- Records
type instance RecordOf (ls :< t) = RecordOf t
instance (HasRecord (Uncovered (ls :< t)), Uncovered (Unwrapped (ls :< t)) ~ t, Covered (Unwrapped (ls :< t)))
      => HasRecord (ls :< t) where record = covered ∘ record



------------------------------------------
-- === Native layers implementation === --
------------------------------------------

-- === Type layer === --

type instance LayerData Type t = Ref Edge (Link (ReShelled t))

instance (MonadSelfBuilder s m, (Link l) ~ Connection s (Ref Node l), Connectible s (Ref Node l) m, l ~ ReShelled a)
      => Creator m (Layer Type a) where
    create = Layer <$> do
        s <- self
        let tgt = Ptr 0 :: Ref Node l -- FIXME[WD]: Pure magic. 0 is the ID of Star
        connection tgt s

instance (Monad m, Destructor m (LayerData Type a)) => Destructor m (Layer Type a) where
    destruct (Layer ref) = destruct ref

-- === Lambda layer === --

-- FIXME[WD->MK]: What is a layer over a "refSet"? It is not informational and should be refactored ASAP.
type instance LayerData Lambda (RefSet t n) = Maybe $ Func.Signature (Ref t n)
instance Monad m => Creator m (Layer Lambda (RefSet t n)) where
    create = return $ Layer Nothing
instance Monad m => Destructor m (Layer Lambda (RefSet t n)) where
    destruct _ = return ()

------------------------------------------
-- === Layer building & destruction === --
------------------------------------------

instance CoverDestructor m (ls :<: a) => Destructor m (ls :<: a) where destruct a = () <$ destructCover a

