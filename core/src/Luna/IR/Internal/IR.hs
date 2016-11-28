{-# LANGUAGE CPP                    #-}
{-# LANGUAGE UndecidableInstances   #-}
{-# LANGUAGE FunctionalDependencies #-}

{-# LANGUAGE NoMonomorphismRestriction #-}
{-# LANGUAGE TypeFamilyDependencies #-}
{-# LANGUAGE OverloadedLabels #-}
{-# LANGUAGE RankNTypes #-}
{-# LANGUAGE TypeApplications #-}
{-# LANGUAGE AllowAmbiguousTypes #-}
{-# LANGUAGE ScopedTypeVariables #-}
{-# LANGUAGE UndecidableSuperClasses #-}

{-# LANGUAGE GADTs #-}





module Luna.IR.Internal.IR where

import qualified Prelude as PP
import           Prelude                      (curry)
import           Luna.Prelude                 hiding (typeRep, Register, register, elem, head, tail, curry, Field2, Enum, Num, Swapped, Curry, String, Integer, Rational, Symbol, Index, Data, Field, Updater', update')
import qualified Luna.Prelude                 as P

import           Data.Base
import           Data.Record                  hiding (Layout, Variants, SymbolMap, symbolMap, Match, Cons, Value, cons, Group, HasValue, ValueOf, value)
import qualified Data.Record                  as Record
import           Type.Cache.TH                (assertTypesEq, cacheHelper, cacheType)
import           Type.Container               hiding (Empty, FromJust, Every)
import           Type.Map                     hiding (Map)
import qualified Data.Map                     as Map
import           Data.Map                     (Map)

import           Data.Typeable                (splitTyConApp, tyConName, typeRepTyCon)
import           Old.Luna.Runtime.Dynamics      (Dynamics, Dynamic, Static, SubDynamics, SubSemiDynamics, ByDynamics)
import qualified Old.Luna.Runtime.Dynamics      as Dynamics
import           Luna.IR.Repr.Styles
import           Luna.IR.Function.Argument
import           Data.Reprx
import           Type.Bool
import           Luna.IR.Term.Format
import Luna.IR.Term.Symbol (Sym, Symbol, IsSymbol, symbol, UncheckedFromSymbol, FromSymbol, uncheckedFromSymbol, fromSymbol, ToSymbol, toSymbol, UniSymbol, uniSymbol, IsUniSymbol)
import qualified Luna.IR.Term.Symbol.Named as N
import Luna.IR.Term.Atom

-- import Data.Shell               as Shell hiding (Access)
import Data.Record.Model.Masked as X (TermRecord, VGRecord2, Store2(Store2), Slot(Slot), Enum(Enum))
import Type.Monoid
import Type.Applicative

import Prologue.Unsafe (error)
-- import Luna.IR.Term (NameByDynamics)
import qualified Luna.IR.Term.Symbol as Symbol
import qualified Data.RTuple as List
import Type.Promotion    (KnownNats, natVals)
import Data.Bits         (setBit, zeroBits)

import Data.RTuple (List, Empty, empty)
import Data.Record.Model.Masked (encode2, EncodeStore, encodeStore, Mask, encodeNat, encodeData2, checkData2, decodeData2, Raw(Raw), unsafeRestore, decodeNat)
import           Data.RTuple (TMap(..), empty, Assoc(..), Assocs, (:=:)) -- refactor empty to another library

import GHC.TypeLits (ErrorMessage(Text, ShowType, (:<>:)))
import Type.Error
-- import Control.Monad.State
import Data.Property
import qualified Data.Property as Prop
import GHC.Stack (HasCallStack, callStack, prettyCallStack, withFrozenCallStack)
import Data.Vector.Mutable (MVector)
import qualified Data.Vector.Mutable as V
import Control.Monad.ST (ST, runST)
import Type.List (Size)
import qualified Type.List as List
import Type.Maybe (FromJust)
import Data.Phantom
import Unsafe.Coerce     (unsafeCoerce)
import Type.Relation (SemiSuper)
import qualified Luna.IR.Term.Layout as Layout
import Luna.IR.Term.Layout (Layout, LayoutOf, Name, Generalize, Universal, universal, Abstract)
import Type.Inference

import qualified Data.Set as Data (Set)
import qualified Data.Set as Set

import Data.Container.List (ToSet, toSet)
import GHC.Prim (Any)

import           Control.Monad.Event     hiding (Any)
import qualified Control.Monad.Event     as Event

import Type.Container (Every)


import Luna.IR.Layer
-- import qualified Luna.IR.Layer as Layer
import Luna.IR.Layer.Model


import Data.Coerced (unsafeCoerced)


import qualified Data.Hetero.Stack as Stack
import           Data.Hetero.Stack (Stack)

import Data.Typeable (Typeable, TypeRep)
import qualified Data.Typeable as Typeable
import Control.Monad.State (StateT, runStateT)
import qualified Control.Monad.State as State
import qualified Data.ManagedVectorMap as MV
import Data.ManagedVectorMap (ManagedVectorMap, ManagedVectorMapM)
import Type.Maybe (FromJust, IsJust)

typeRep :: forall a. Typeable a => TypeRep
typeRep = Typeable.typeRep (Proxy :: Proxy a) ; {-# INLINE typeRep #-}

type Typeables ts = Constraints $ Typeable <$> ts



class IsIdx t where
    idx :: Iso' t Int
    default idx :: (Wrapped t, Unwrapped t ~ Int) => Lens' t Int
    idx = wrapped' ; {-# INLINE idx #-}

--
-- --------------------
-- -- === Events === --
-- --------------------
--
-- -- === Definition === --
--
-- type Register        t a m = Event        (t (Universal a)) m (Universal a)
-- type DelayedRegister t a m = DelayedEvent (t (Universal a)) m (Universal a)
--
--
-- -- === Registration === --
--
-- register :: forall t a m. Register t a m => a -> m a
-- register a = a <$ dispatch_ @(t (Universal a)) (universal a) ; {-# INLINE register #-}
--
-- delayedRegister :: forall t a m. DelayedRegister t a m => a -> m a
-- delayedRegister a = a <$ delayedDispatch_ @(t (Universal a)) (universal a) ; {-# INLINE delayedRegister #-}
--
--
-- -- === Event types === --
--
-- data New a





---------------------------

------------------
-- === Elem === --
------------------

newtype Elem = Elem Int deriving (Show)
makeWrapped '' Elem

class IsElem a where
    elem :: Iso' a Elem
    default elem :: (Wrapped a, Unwrapped a ~ Elem) => Iso' a Elem
    elem = wrapped' ; {-# INLINE elem #-}

instance IsIdx Elem where
    idx = wrapped' ; {-# INLINE idx #-}


---------------------
-- === IRStore === --
---------------------

-- === Definition === --

type LayerRep = TypeRep
type ElemRep  = TypeRep


data IRState   m = IRState   { _elems         :: Map ElemRep $ ElemStore m
                             , _attrs         :: Map LayerRep Any
                             , _genericLayers :: LayerConsStore m
                             }

data ElemStore m = ElemStore { _layerValues   :: ManagedVectorMapM m LayerRep Any
                             , _elemLayers    :: LayerConsStore m
                             }

type LayerConsStore m = Map LayerRep (AnyCons m)

makeLenses ''ElemStore
makeLenses ''IRState


-- === Accessors === --

specificLayers :: ElemRep -> Traversal' (IRState m) (LayerConsStore m)
specificLayers el = elems . ix el . elemLayers ; {-# INLINE specificLayers #-}


-- === Instances === --

instance Default (ElemStore m) where def = ElemStore def def
instance Default (IRState   m) where def = IRState   def def def



-----------------
-- === IRT === --
-----------------

-- === Definition === --

newtype IRT m a = IRT (StateT (IRState (IRT m)) m a) deriving (Functor, Applicative, Monad, MonadIO, MonadFix)
makeWrapped ''IRT

type IRState' m = IRState (GetIRMonad m)

type        GetIRMonad    m = IRT (GetIRSubMonad m)
type family GetIRSubMonad m where
            GetIRSubMonad (IRT m) = m
            GetIRSubMonad (t   m) = GetIRSubMonad m


-- === Accessors === --

atElem :: Functor m => ElemRep -> (Maybe (ElemStore m) -> m (Maybe (ElemStore m))) -> IRState m -> m (IRState m)
atElem = elems .: at  ; {-# INLINE atElem #-}

modifyElem  ::              ElemRep -> (ElemStore m ->    ElemStore m)  -> IRState m ->    IRState m
modifyElemM :: Functor m => ElemRep -> (ElemStore m -> m (ElemStore m)) -> IRState m -> m (IRState m)
modifyElem  e f = elems %~ Map.insertWith (const f) e (f def) ; {-# INLINE modifyElem  #-}
modifyElemM e f = atElem e $ fmap Just . f . fromMaybe def    ; {-# INLINE modifyElemM #-}


-- === Querying === --

lookupGenericLayerCons :: LayerRep -> IRState m -> Maybe (AnyCons m)
lookupGenericLayerCons l s = s ^? genericLayers . ix l ; {-# INLINE lookupGenericLayerCons #-}

lookupSpecificLayerCons :: ElemRep -> LayerRep -> IRState m -> Maybe (AnyCons m)
lookupSpecificLayerCons el l s = s ^? specificLayers el . ix l ; {-# INLINE lookupSpecificLayerCons #-}

lookupLayerCons :: ElemRep -> LayerRep -> IRState m -> Maybe (AnyCons m)
lookupLayerCons el l s = lookupSpecificLayerCons el l s <|> lookupGenericLayerCons l s ; {-# INLINE lookupLayerCons #-}


-- === Construction === --

newElem :: forall t m. (IRMonad m, Typeable (Abstract t), PrimMonad (GetIRMonad m), IsElem t) => Definition t -> m t
newElem dt = do
    d <- getIRState
    let trep = typeRep @(Abstract t)
        Just layerStore = d ^? elems  . ix trep . layerValues
        consLayer t i l elemStore = do
            let Just consFunc = lookupLayerCons trep l d -- FIXME[WD]: internal error when cons was not registered
            runInIR $ MV.unsafeWrite elemStore i =<< unsafeAppCons consFunc t dt
    (i, layerStore') <- runInIR $ MV.reserveIdx layerStore -- FIXME[WD]: refactor these lines - they reserve new idx
    putIRState $ d & elems . ix trep . layerValues .~ layerStore'
    let el = i ^. from (elem . idx)
    mapM_ (uncurry $ consLayer el i) (MV.assocs layerStore)
    return el


-- === Registration === --

registerElemWith :: forall el m. (Typeable el, IRMonad m) => (ElemStore (GetIRMonad m) -> ElemStore (GetIRMonad m)) -> m ()
registerElemWith f = modifyIRState_ $ modifyElem (typeRep @el) f
{-# INLINE registerElemWith #-}

registerElem :: forall el m. (Typeable el, IRMonad m) => m ()
registerElem = registerElemWith @el id ; {-# INLINE registerElem #-}

registerGenericLayer :: forall layer t m. (IRMonad m, Typeable layer)
                     => LayerCons' layer t (GetIRMonad m) -> m ()
registerGenericLayer f = modifyIRState_ $ genericLayers %~ Map.insert (typeRep @layer) (anyCons @layer f)
{-# INLINE registerGenericLayer #-}

registerElemLayer :: forall at layer t m. (IRMonad m, Typeable at, Typeable layer)
                  => LayerCons' layer t (GetIRMonad m) -> m ()
registerElemLayer f = modifyIRState_ $ specificLayers (typeRep @at) %~ Map.insert (typeRep @layer) (anyCons @layer f)
{-# INLINE registerElemLayer #-}

attachLayer :: (IRMonad m, PrimMonad (GetIRMonad m)) => LayerRep -> ElemRep -> m ()
attachLayer l e = modifyIRStateM_ $ runInIR . modifyElemM e (layerValues $ MV.unsafeAddKey l)
{-# INLINE attachLayer #-}



----------------------
-- === IRMonad === ---
----------------------

-- === Definition === --

-- | IRMonad is subclass of MonadFic because many term operations reuire recursive calls.
--   It is more convenient to store it as global constraint, so it could be altered easily in the future.
type  IRMonadBase       m = (PrimMonad m, MonadFix m)
type  IRMonadInvariants m = (IRMonadBase m, IRMonadBase (GetIRSubMonad m), IRMonad (GetIRMonad m))
class IRMonadInvariants m => IRMonad m where
    getIRState :: m (IRState' m)
    putIRState :: IRState' m -> m ()
    runInIR    :: GetIRMonad m a -> m a

instance {-# OVERLAPPABLE #-} (MonadFix m, PrimMonad m) => IRMonad (IRT m) where
    getIRState = wrap'   State.get ; {-# INLINE getIRState #-}
    putIRState = wrap' . State.put ; {-# INLINE putIRState #-}
    runInIR    = id                ; {-# INLINE runInIR    #-}

instance {-# OVERLAPPABLE #-} IRMonadTrans t m => IRMonad (t m) where
    getIRState = lift   getIRState ; {-# INLINE getIRState #-}
    putIRState = lift . putIRState ; {-# INLINE putIRState #-}
    runInIR    = lift . runInIR    ; {-# INLINE runInIR    #-}

type IRMonadTrans t m = (IRMonad m, MonadTrans t, IRMonadBase (t m), GetIRMonad (t m) ~ GetIRMonad m)


-- === Modyfication === --

modifyIRStateM :: IRMonad m => (IRState' m -> m (a, IRState' m)) -> m a
modifyIRStateM f = do
    s <- getIRState
    (a, s') <- f s
    putIRState s'
    return a
{-# INLINE modifyIRStateM #-}

modifyIRStateM_ :: IRMonad m => (IRState' m -> m (IRState' m)) -> m ()
modifyIRStateM_ = modifyIRStateM . fmap (fmap ((),)) ; {-# INLINE modifyIRStateM_ #-}

modifyIRState_ :: IRMonad m => (IRState' m -> IRState' m) -> m ()
modifyIRState_ = modifyIRStateM_ . fmap return ; {-# INLINE modifyIRState_ #-}


-- === Running === --

runIRT :: forall t m a. Monad m => IRT m a -> m a
runIRT m = State.evalStateT (unwrap' m) def ; {-# INLINE runIRT #-}


-- === Instances === --

instance MonadTrans IRT where
    lift = wrap' . lift ; {-# INLINE lift #-}

instance PrimMonad m => PrimMonad (IRT m) where
    type PrimState (IRT m) = PrimState m
    primitive = lift . primitive ; {-# INLINE primitive #-}



------------------
-- === Keys === --
------------------

--- === Definition === --

newtype Key k = Key (KeyData k)

newtype KeyDataST m key = KeyDataST (KeyTargetST m key)
data    KeyData     key where
        KeyData :: KeyDataST m key -> KeyData key

type family KeyTargetST (m :: * -> *) key


makeWrapped '' KeyDataST
makeWrapped '' Key


-- === Key Monad === --

class Monad m => KeyMonad key m where
    uncheckedLookupKeyDataST :: m (Maybe (KeyDataST m key))
--
uncheckedLookupKey :: KeyMonad key m => m (Maybe (Key key))
uncheckedLookupKey = (Key . keyData) .: uncheckedLookupKeyDataST ; {-# INLINE uncheckedLookupKey #-}


-- === Construction === --

keyData :: KeyDataST m k -> KeyData k
keyData = KeyData ; {-# INLINE keyData #-}

unsafeeFromKeyData :: KeyData k -> KeyDataST m k
unsafeeFromKeyData (KeyData d) = unsafeCoerce d ; {-# INLINE unsafeeFromKeyData #-}

unsafeReadLayerST :: (IRMonad m, IsElem t) => KeyDataST m (Layer (Abstract t) layer) -> t -> m (LayerData layer t)
unsafeReadLayerST key t = unsafeCoerce <$> runInIR (MV.unsafeRead (t ^. elem . idx) (unwrap' key)) ; {-# INLINE unsafeReadLayerST #-}

unsafeReadLayer :: (IRMonad m, IsElem t) => Key (Layer (Abstract t) layer) -> t -> m (LayerData layer t)
unsafeReadLayer k = unsafeReadLayerST (unsafeeFromKeyData $ unwrap' k) ; {-# INLINE unsafeReadLayer #-}

unpackKey :: forall k m. Monad m => Key k -> m (KeyTargetST m k)
unpackKey k = return $ unwrap' (unsafeeFromKeyData $ unwrap' k :: KeyDataST m k) ; {-# INLINE unpackKey #-}

packKey :: forall k m. Monad m => KeyTargetST m k -> m (Key k)
packKey k = return . wrap' . keyData $ (wrap' k :: KeyDataST m k) ; {-# INLINE packKey #-}


-- === Key access === --

type Accessible k m = (Readable k m, Writable k m)

-- Readable
class    Monad m                                => Readable k m     where getKey :: m (Key k)
instance {-# OVERLAPPABLE #-} SubReadable k t m => Readable k (t m) where getKey = lift getKey ; {-# INLINE getKey #-}
type SubReadable k t m = (Readable k m, MonadTrans t, Monad (t m))

-- Writable
class    Monad m                                => Writable k m     where putKey :: Key k -> m ()
instance {-# OVERLAPPABLE #-} SubWritable k t m => Writable k (t m) where putKey = lift . putKey ; {-# INLINE putKey #-}
type SubWritable k t m = (Writable k m, MonadTrans t, Monad (t m))


readLayer :: forall layer t m. (IRMonad m, IsElem t, Readable (Layer (Abstract t) layer) m ) => t -> m (LayerData layer t)
readLayer t = flip unsafeReadLayer t =<< getKey @(Layer (Abstract t) layer)

readKey :: forall k m. Readable k m => m (KeyTargetST m k)
readKey = unpackKey =<< getKey @k ; {-# INLINE readKey #-}

writeKey :: forall k m. Writable k m => KeyTargetST m k -> m ()
writeKey = putKey @k <=< packKey ; {-# INLINE writeKey #-}


-- === Errors === --

type KeyAccessError action k = Sentence $ 'Text "Key"
                                    :</>: Ticked ('ShowType k)
                                    :</>: 'Text "is not"
                                    :</>: ('Text action :<>: 'Text "able")

type KeyMissingError k = Sentence $ 'Text "Key"
                              :</>: Ticked ('ShowType k)
                              :</>: 'Text "is not accessible"

type KeyReadError  k = KeyAccessError "read"  k
type KeyWriteError k = KeyAccessError "write" k



-----------------------
-- === Key types === --
-----------------------

-- === Definitions === --

type instance KeyTargetST m (Layer _ _) = MV.VectorRefM (GetIRMonad m) Any -- FIXME: make the type nicer
type instance KeyTargetST m (Net   _)   = ElemStore     (GetIRMonad m)


-- === Aliases === --

data Net  t
data Attr t

type LayerKey el l = Key (Layer el l)
type NetKey  n     = Key (Net   n)
type AttrKey  a    = Key (Attr  a)


-- === Instances === --

instance (IRMonad m, Typeable e, Typeable l) => KeyMonad (Layer e l) m where
    uncheckedLookupKeyDataST = fmap wrap' . (^? (elems . ix (typeRep @e) . layerValues . ix (typeRep @l))) <$> getIRState ; {-# INLINE uncheckedLookupKeyDataST #-}

instance (IRMonad m, Typeable e) => KeyMonad (Net e) m where
    uncheckedLookupKeyDataST = fmap wrap' . (^? (elems . ix (typeRep @e))) <$> getIRState ; {-# INLINE uncheckedLookupKeyDataST #-}







-------------------
-- === Link === --
-------------------

-- === Definition === --

newtype Link  a b = Link Elem deriving (Show)
type    Link' a   = Link a a
type instance Definition (Link a b) = (a,b)
makeWrapped ''Link

type SubLink s t = Link (Sub s t) t

-- === Abstract === --

data LINK  a b
type LINK' a = LINK a a
type instance Abstract  (Link a b) = LINK (Abstract  a) (Abstract  b)


-- === Construction === --

link :: forall a b m. (IRMonad m, Typeable (Abstract a), Typeable (Abstract b))
     => a -> b -> m (Link a b)
link a b = newElem (a,b) ; {-# INLINE link #-}


-- === Instances === --

instance      IsElem    (Link a b)
type instance Universal (Link a b) = Link (Universal a) (Universal b)




------------------------
-- === TermSymbol === --
------------------------

data XXX -- FIXME

newtype TermSymbol    atom t = TermSymbol (N.Symbol atom (Layout.Named (SubLink Name t) (SubLink Atom t)))
type    TermSymbol'   atom   = TermSymbol atom XXX
newtype TermUniSymbol      t = TermUniSymbol (N.UniSymbol (Layout.Named (SubLink Name t) (SubLink Atom t)))
makeWrapped ''TermSymbol
makeWrapped ''TermUniSymbol


-- === Helpers === --

hideLayout :: TermSymbol atom t -> TermSymbol atom XXX
hideLayout = unsafeCoerce ; {-# INLINE hideLayout #-}


-- === Layout validation === ---
-- | Layout validation. Type-assertion utility, proving that symbol construction is not ill-typed.

type InvalidFormat sel a format = 'ShowType sel
                             :</>: Ticked ('ShowType a)
                             :</>: 'Text  "is not a valid"
                             :</>: Ticked ('ShowType format)


class                                                       ValidateScope scope sel a
instance {-# OVERLAPPABLE #-} ValidateScope_ scope sel a => ValidateScope scope sel a
instance {-# OVERLAPPABLE #-}                               ValidateScope I     sel a
instance {-# OVERLAPPABLE #-}                               ValidateScope scope I   a
instance {-# OVERLAPPABLE #-}                               ValidateScope scope sel I
type ValidateScope_ scope sel a = Assert (a `In` Atoms scope) (InvalidFormat sel a scope)


class                                                        ValidateLayout model sel a
instance {-# OVERLAPPABLE #-} ValidateLayout_ model sel a => ValidateLayout model sel a
instance {-# OVERLAPPABLE #-}                                ValidateLayout I     sel a
instance {-# OVERLAPPABLE #-}                                ValidateLayout model I   a
instance {-# OVERLAPPABLE #-}                                ValidateLayout model sel I
type ValidateLayout_ model sel a = ValidateScope (model # sel) sel a
type ValidateLayout' t     sel a = ValidateLayout (t # Layout) sel a


-- === Instances === --

-- FIXME: [WD]: it seems that Layout in the below declaration is something else than real layout - check it and refactor
type instance Access Layout (TermSymbol atom t) = Access Layout (Unwrapped (TermSymbol atom t))
type instance Access Atom   (TermSymbol atom t) = atom
type instance Access Format (TermSymbol atom t) = Access Format atom
type instance Access Sym    (TermSymbol atom t) = TermSymbol atom t

instance Accessor Sym (TermSymbol atom t) where access = id ; {-# INLINE access #-}

instance UncheckedFromSymbol (TermSymbol atom t) where uncheckedFromSymbol = wrap' ; {-# INLINE uncheckedFromSymbol #-}

instance ValidateLayout (LayoutOf t) Atom atom
      => FromSymbol (TermSymbol atom t) where fromSymbol = wrap' ; {-# INLINE fromSymbol #-}


-- Repr
instance Repr s (Unwrapped (TermSymbol atom t))
      => Repr s (TermSymbol atom t) where repr = repr . unwrap' ; {-# INLINE repr #-}

-- Fields
type instance FieldsType (TermSymbol atom t) = FieldsType (Unwrapped (TermSymbol atom t))
instance HasFields (Unwrapped (TermSymbol atom t))
      => HasFields (TermSymbol atom t) where fieldList = fieldList . unwrap' ; {-# INLINE fieldList #-}



----------------------
-- === TermData === --
----------------------

type TermStoreSlots = '[ Atom ':= Enum, Format ':= Mask, Sym ':= Raw ]
type TermStore = Store2 TermStoreSlots

newtype TermData sys model = TermData TermStore deriving (Show)
makeWrapped ''TermData


-- === Encoding === --

class                                                              SymbolEncoder atom where encodeSymbol :: forall t. TermSymbol atom t -> TermStore
instance                                                           SymbolEncoder I    where encodeSymbol = impossible
instance EncodeStore TermStoreSlots (TermSymbol' atom) Identity => SymbolEncoder atom where
    encodeSymbol = runIdentity . encodeStore . hideLayout ; {-# INLINE encodeSymbol #-} -- magic



------------------
-- === Term === --
------------------

-- === Definition === --

newtype Term  layout = Term Elem deriving (Show)
type    Term'        = Term Draft
makeWrapped ''Term

type instance Definition (Term _) = TermStore

-- === Abstract === --

data TERM
type instance Abstract (Term _) = TERM

-- === Construction === --

term :: forall atom layout m. (SymbolEncoder atom, IRMonad m)
     => TermSymbol atom (Term layout) -> m (Term layout)
term a = newElem (encodeSymbol a) ; {-# INLINE term #-}

-- === Instances === --

instance      IsElem    (Term l)
type instance Universal (Term _) = Term'
type instance Sub s     (Term l) = Term (Sub s l)





-- -------------------------------------
-- === Expr Layout type caches === --
-------------------------------------

type instance Encode2 Atom    v = List.Index v (Every Atom)
type instance Encode2 Format  v = List.Index v (Every Format)
