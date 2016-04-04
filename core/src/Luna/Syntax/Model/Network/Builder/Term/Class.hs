{-# LANGUAGE NoMonomorphismRestriction #-}
{-# LANGUAGE FunctionalDependencies #-}
{-# LANGUAGE UndecidableInstances   #-}
{-# LANGUAGE RecursiveDo            #-}

{-# LANGUAGE PolyKinds            #-}

module Luna.Syntax.Model.Network.Builder.Term.Class (module Luna.Syntax.Model.Network.Builder.Term.Class, module X) where

import Prelude.Luna    hiding (Num)
import Prologue.Unsafe (undefined)

import           Control.Monad.Event
import           Data.Direction
import           Data.Graph
import           Data.Graph.Builders
import           Data.Layer_OLD
import           Data.Layer_OLD.Cover_OLD
import           Data.Prop
import qualified Data.Record                             as Record
import           Data.Record                             (RecordOf, IsRecord, HasRecord, record, asRecord, Variant, MapTryingElemList_, withElement_, Props, withElement', Layout_Variants, MapTryingElemList, OverElement, overElement)
import           Luna.Runtime.Dynamics                 as Runtime
import           Luna.Syntax.Term.Function.Argument
import           Luna.Syntax.Term.Function.Argument       as X (arg)
import           Luna.Syntax.Term.Expr                    hiding (Val, Lit, Thunk, Expr, Draft, Source, Name)
import qualified Luna.Syntax.Term.Expr                    as Term
import qualified Data.Graph.Builder                      as GraphBuilder
import           Luna.Syntax.Model.Layer                 (Type, Markable, TCData, Meta, Name, Lambda, (:<:), (:<))
import           Luna.Compilation.Pass.Interpreter.Layer (InterpreterData)
import           Luna.Syntax.Model.Network.Builder.Layer
import qualified Luna.Syntax.Model.Network.Builder.Self  as Self
import qualified Luna.Syntax.Model.Network.Builder.Type  as Type
import           Luna.Syntax.Model.Network.Term
import qualified Luna.Syntax.Term.Lit                as Lit
import           Control.Monad.Trans.Identity
import           Type.Bool

import qualified Data.Graph.Backend.NEC as NEC
import           Data.Graph.Model.Pointer.Set (RefSet)

import Control.Monad.Delayed (delayed, MonadDelayed)
import Data.Graph.Builder (write)
import qualified Control.Monad.State as State
import Control.Monad.Primitive (PrimState, PrimMonad)

-------------------------------------
-- === Term building utilities === --
-------------------------------------

-- === Utility Type Families === --

-- FIXME[WD]: Zmienic nazwe Layout na adekwatna
-- FIXME[WD]: Skoro Layout okresla jakie jest "wejscie" do nodu, to Input nie jest potrzebny, bo mozna go wyinferowac jako odwrotnosc Connection

type family BuildArgs (t :: k) n :: *
type family BuildArgs2 t n :: *
type family Expanded  (t :: k) n :: *


-- === ElemBuilder === --

class    ElemBuilder el m  a where buildElem :: el -> m a
instance {-# OVERLAPPABLE #-} ElemBuilder el IM a where buildElem = impossible


class    ElemBuilder2 el m  a where buildElem2 :: el -> m a
instance {-# OVERLAPPABLE #-} ElemBuilder2 el IM a where buildElem2 = impossible


instance {-# OVERLAPPABLE #-}
         ( Record.Cons el (Uncovered a)
         , CoverConstructor m a
         , Dispatcher ELEMENT a m
         , Self.MonadSelfBuilder s m
         , Castable a s
         ) => ElemBuilder el m a where
    -- TODO[WD]: change buildAbsMe to buildMe2
    --           and fire monad every time we construct an element, not once for the graph
    buildElem el = dispatch ELEMENT =<< Self.buildAbsMe (constructCover $ Record.cons el) where
    {-# INLINE buildElem #-}

instance {-# OVERLAPPABLE #-}
         ( CoverConstructor m a
         , Uncovered a ~ el
         , Dispatcher ELEMENT a m
         , Self.MonadSelfBuilder s m
         , Castable a s
         ) => ElemBuilder2 el (m :: * -> *) a where
    -- TODO[WD]: change buildAbsMe to buildMe2
    --           and fire monad every time we construct an element, not once for the graph
    buildElem2 el = dispatch ELEMENT =<< Self.buildAbsMe (constructCover el) where
    {-# INLINE buildElem2 #-}


-- === TermBuilder === --

class TermBuilder (t :: k) m a where buildTerm :: Proxy t -> BuildArgs t a -> m a
instance {-# OVERLAPPABLE #-} TermBuilder t IM a where buildTerm = impossible


class TermBuilder2 t m a where buildTerm2 :: t -> BuildArgs2 t a -> m a
instance {-# OVERLAPPABLE #-} TermBuilder2 t IM a where buildTerm2 = impossible




newtype BindBuilder t m a = BindBuilder (IdentityT m a) deriving (Show, Functor, Applicative, Monad, MonadTrans)
makeWrapped ''BindBuilder

runBindBuilder :: BindBuilder t m t -> m t
runBindBuilder = runIdentityT ∘ unwrap'


-------------------------------
-- === Term constructors === --
-------------------------------


-- === Lit === --

type instance BuildArgs Lit.Star   n = ()
type instance BuildArgs Lit.String n = OneTuple Lit.String
type instance BuildArgs Lit.Number n = OneTuple Lit.Number

instance ElemBuilder Lit.Star   m a => TermBuilder Lit.Star   m a where buildTerm p ()           = buildElem Lit.Star
instance ElemBuilder Lit.String m a => TermBuilder Lit.String m a where buildTerm p (OneTuple s) = buildElem s
instance ElemBuilder Lit.Number m a => TermBuilder Lit.Number m a where buildTerm p (OneTuple s) = buildElem s

star :: TermBuilder Lit.Star m a => m a
star = curry $ buildTerm (Proxy :: Proxy Lit.Star)

str :: TermBuilder Lit.String m a => String -> m a
str = (curry $ buildTerm (Proxy :: Proxy Lit.String)) ∘ Lit.String

ratio :: TermBuilder Lit.Number m a => Rational -> m a
ratio = (curry $ buildTerm (Proxy :: Proxy Lit.Number)) ∘ Lit.decimal ∘ Lit.Rational

int :: TermBuilder Lit.Number m a => Integer -> m a
int = (curry $ buildTerm (Proxy :: Proxy Lit.Number)) ∘ Lit.decimal ∘ Lit.Integer

double :: TermBuilder Lit.Number m a => Double -> m a
double = (curry $ buildTerm (Proxy :: Proxy Lit.Number)) ∘ Lit.decimal ∘ Lit.Double

number :: TermBuilder Lit.Number m a => Lit.Number -> m a
number = (curry $ buildTerm (Proxy :: Proxy Lit.Number))

-- === Val === --

type instance BuildArgs Cons n = (NameInput n, [Arg (Input n)])
type instance BuildArgs Lam  n = ([Arg (Input n)], Input n)

instance ( name ~ NameInput a
         , inp ~ Input a
         , MonadFix m
         , Connectible     inp  a m
         , ConnectibleName name a m
         , ElemBuilder (Cons (NameConnection name a) (Ref Edge (Connection inp a))) m a
         ) => TermBuilder Cons m a where
    buildTerm p (name, args) = mdo
        out   <- buildElem $ Cons cname cargs
        cname <- nameConnection name out
        cargs <- (mapM ∘ mapM) (flip connection out) args
        return out

instance ( inp ~ Input a
         , MonadFix m
         , Connectible inp a m
         , ElemBuilder (Lam $ Ref Edge (Connection inp a)) m a
         ) => TermBuilder Lam m a where
    buildTerm p (args, res) = mdo
        out   <- buildElem $ Lam cargs cres
        cargs <- (mapM ∘ mapM) (flip connection out) args
        cres  <- connection res out
        return out


cons :: TermBuilder Cons m a => NameInput a -> [Arg $ Input a] -> m a
cons = curry $ buildTerm (Proxy :: Proxy Cons)

lam :: TermBuilder Lam m a => [Arg $ Input a] -> Input a -> m a
lam = curry $ buildTerm (Proxy :: Proxy Lam)


-- === Thunk === --

type instance BuildArgs Acc n    = (NameInput n, Input n)
type instance BuildArgs App n    = (Input n, [Arg (Input n)])
type instance BuildArgs Native n = OneTuple (NameInput n)

instance {-# OVERLAPPABLE #-}
         ( src  ~ Input a
         , name ~ NameInput a
         , MonadFix m
         , Connectible     src  a m
         , ConnectibleName name a m
         , ElemBuilder (Acc (NameConnection name a) (Ref Edge (Connection src a))) m a
         ) => TermBuilder Acc m a where
    buildTerm p (name, src) = mdo
        out   <- buildElem $ Acc cname csrc
        cname <- nameConnection name out
        csrc  <- connection     src  out
        return out

instance ( inp ~ Input a
         , MonadFix m
         , Connectible inp a m
         , ElemBuilder (App $ Ref Edge (Connection inp a)) m a
         ) => TermBuilder App m a where
    buildTerm p (src, args) = mdo
        out   <- buildElem $ App csrc cargs
        csrc  <- connection src out
        cargs <- (mapM ∘ mapM) (flip connection out) args
        return out

instance ( name ~ NameInput a
         , MonadFix m
         , ConnectibleName name a m
         , ElemBuilder (Native $ NameConnection name a) m a
         ) => TermBuilder Native m a where
    buildTerm p (OneTuple name) = mdo
        out   <- buildElem $ Native cname
        cname <- nameConnection name out
        return out

acc :: TermBuilder Acc m a => NameInput a -> Input a -> m a
acc = curry $ buildTerm (Proxy :: Proxy Acc)

app :: TermBuilder App m a => Input a -> [Arg $ Input a] -> m a
app = curry $ buildTerm (Proxy :: Proxy App)

native :: TermBuilder Native m a => NameInput a -> m a
native = curry $ buildTerm (Proxy :: Proxy Native)

-- === Expr === --


type instance BuildArgs Var n = OneTuple (NameInput n)
instance ( name ~ NameInput a
         , MonadFix m
         , ConnectibleName name a m
         , ElemBuilder (Var $ NameConnection name a) m a
         ) => TermBuilder Var m a where
    buildTerm p (OneTuple name) = mdo
        out   <- buildElem $ Var cname
        cname <- nameConnection name out
        return out

type instance BuildArgs Unify n = (Input n, Input n)
instance ( inp ~ Input a
         , MonadFix m
         , Connectible inp a m
         , ElemBuilder (Unify $ Ref Edge (Connection inp a)) m a
         ) => TermBuilder Unify m a where
    buildTerm p (a,b) = mdo
        out <- buildElem $ Unify ca cb
        ca  <- connection a out
        cb  <- connection b out
        return out

type instance BuildArgs Match n = (Input n, Input n)
instance ( inp ~ Input a
         , MonadFix m
         , Connectible inp a m
         , ElemBuilder (Match $ Ref Edge (Connection inp a)) m a
         ) => TermBuilder Match m a where
    buildTerm p (a,b) = mdo
        out <- buildElem $ Match ca cb
        ca  <- connection a out
        cb  <- connection b out
        return out


        --type instance BuildArgs Unify n = (Input n, Input n)
        --instance ( a ~ Input a
        --         , Record.Cons (Unify (Param a)) (TermOf a)
        --         , ElemBuilder3 n   a
        --         , Parametrized        n   a
        --         , ParamResolver          n m a
        --         ) => TermBuilder Unify m a where
        --    buildTerm p (a,b) = term $ unifyCons <$> param a <*> param b


type instance BuildArgs2 Var' a = OneTuple (NameInput a)
instance TermBuilderCtx Var' n m a => TermBuilder2 Var' m a where
    buildTerm2 p (OneTuple a) = term $ varCons <$> nameParam a

type instance BuildArgs2 Match' a = (a,a)
instance TermBuilderCtx Match' n m a => TermBuilder2 Match' m a where
    buildTerm2 p (a,b) = term $ matchCons <$> param a <*> param b

type instance BuildArgs2 Unify' a = (a, a)
instance TermBuilderCtx Unify' n m a => TermBuilder2 Unify' m a where
    buildTerm2 p (a,b) = term $ unifyCons <$> param a <*> param b


varCons   = Record.cons ∘  Var
unifyCons = Record.cons ∘∘ Unify
matchCons = Record.cons ∘∘ Match

type family Parameterized t a
type instance Parameterized Var'   a = Var   $ NameParam a
type instance Parameterized Unify' a = Unify $ Param     a
type instance Parameterized Match' a = Match $ Param     a


type TermBuilderCtx t n m a = ( Record.Cons (Parameterized t a) (TermOf a)
                              , ElemBuilder3  n   a
                              , Parametrized  n   a
                              , ParamResolver n m a
                              )

--type ElemBuilder3 a = SmartCons (Match (Param a)) (TermOf a)

--class TermBuilder (t :: k) m a where buildTerm :: Proxy t -> BuildArgs t a -> m a

--class

--term :: (ParamResolver n m a, ElemBuilder3 m a)
--             => BindBuilder a (n m) (TermOf a) -> m a

term :: (ElemBuilder3 n a, ParamResolver n m a) => BindBuilder a n (TermOf a) -> m a
term m = resolveParams $ runBindBuilder $ (lift ∘ buildElem3) =<< m

var :: TermBuilder Var m a => NameInput a -> m a
var = curry $ buildTerm (Proxy :: Proxy Var)

unify :: TermBuilder Unify m a => Input a -> Input a -> m a
unify = curry $ buildTerm (Proxy :: Proxy Unify)

match :: TermBuilder Match m a => Input a -> Input a -> m a
match = curry $ buildTerm (Proxy :: Proxy Match)




type family Param a
type family NameParam a
type family Source2 a

--class ElemBuilder3 m a where
--    nameParam   :: NameInput a -> m (NameParam a)
--    param       :: a -> m (Param a)
--    buildElem3 :: Source2 a -> m a

--class TransRunner n m

--class (MonadTrans n, Monad (n m), Monad m) => ParamResolver n m a | m a -> n where
--    resolveParams :: n m a -> m a

class (Monad n, Monad m) => ParamResolver n m a | m a -> n where
    resolveParams :: n a -> m a


--newtype Root a   = Root a   deriving (Show, Functor, Foldable, Traversable)
--newtype Slot a   = Slot a   deriving (Show, Functor, Foldable, Traversable)
--data    Bind a b = Bind a b deriving (Show)


--class ParamResolver t m a | t -> a where
--    resolveParam :: t -> m ()

--instance ( GraphBuilder.MonadBuilder (Hetero (VectorGraph node edge cluster)) m
--         , ParamResolver t m v
--         , State.MonadState [(a, Ref Edge a)] m
--         ) => ParamResolver (Bind t a) m v where
--    resolveParam (Bind t a) = do
--        cref <- reserveConnection
--        State.modify ((a, cref):)
--        resolveParam t -- dorobic aplikowanie trzeba







class Parametrized m t where
    nameParam   :: NameInput t -> BindBuilder t m (NameParam t)
    param       :: t           -> BindBuilder t m     (Param t)


class ElemBuilder3 m t where
    buildElem3 :: TermOf t -> m t


--instance ElemBuilder3 m t where buildElem3 = undefined

--class NamedConnectionReservation     src tgt m conn where reserveNamedConnection  ::             src -> Proxy tgt -> m conn


instance ( GraphBuilder.MonadBuilder (Hetero (NEC.Graph node edge cluster)) m
         , NamedConnectionReservation (NameInput (Ref Node a)) (Ref Node a) m (NameParam (Ref Node a))
         , State.MonadState [(Ref Node a, Ref Edge (Link a))] m
         ) => Parametrized m (Ref Node a) where
    param t = do
        cref <- reserveConnection
        lift $ State.modify ((t, cref):) -- FIXME[WD]: remove lift
        return cref
    -- FIXME[WD]: add the logic for dynamic graphs to add pending connections to state, like above
    nameParam name = lift $ reserveNamedConnection name (p :: P (Ref Node a))
    {-# INLINE param     #-}
    {-# INLINE nameParam #-}


--write :: (MonadBuilder t m, Referred r a t) => Ref r a -> a -> m ()
--write ref = modify_ ∘ set (focus ref)



type instance TermOf (Ref t a) = TermOf a
type instance Param (Ref Node a) = Ref Edge (Link a)
type instance NameParam (Ref Node a) = Lit.String -- FIXME[WD]: Support dynamic terms

--Term t Val  Static


--Term t term rt

--class TermBuilder t term rt where
--    param ::


-- === Draft === --

type instance BuildArgs   Blank n = ()
instance      ElemBuilder Blank m a => TermBuilder Blank m a where buildTerm p () = buildElem Blank

blank :: TermBuilder Blank m a => m a
blank = curry $ buildTerm (Proxy :: Proxy Blank)






matchType' :: t -> t -> t
matchType' = const





matchType :: Proxy t -> t -> t
matchType _ = id

matchTypeM :: Proxy t -> m t -> m t
matchTypeM _ = id




------------------------------
-- === Network Building === --
------------------------------

type NetClusterLayers = '[Lambda, Name]
type NetLayers        = '[Type, Succs, TCData, InterpreterData, Meta]
type NetLayers'       = '[Type, Succs]
type NetNode          = NetLayers  :<: Draft Static
type NetNode'         = NetLayers' :<: Draft Static
type NetRawNode       = NetLayers  :<: Raw
type NetCluster       = NetClusterLayers :< RefSet Node NetNode
type NetCluster'      = NetClusterLayers :< RefSet Node NetNode'
type NetRawCluster    = NetClusterLayers :< RefSet Node NetRawNode

type NetGraph   = Hetero (NEC.Graph NetRawNode (Link NetRawNode) NetRawCluster)
type NetGraph'  = Hetero (NEC.Graph NetNode    (Link NetNode)    NetCluster)

type NetGraph'' = Hetero (NEC.Graph NetNode'   (Link NetNode')   NetCluster')

buildNetwork  = runIdentity ∘ buildNetworkM
buildNetworkM = rebuildNetworkM' (def :: NetGraph)

rebuildNetwork' = runIdentity .: rebuildNetworkM'
rebuildNetworkM' (net :: NetGraph) = flip Self.evalT (undefined ::        Ref Node NetNode)
                                     ∘ flip Type.evalT (Nothing   :: Maybe (Ref Node NetNode))
                                     ∘ constrainTypeM1 CONNECTION (Proxy :: Proxy $ Ref Edge c)
                                     ∘ constrainTypeEq ELEMENT    (Proxy :: Proxy $ Ref Node NetNode)
                                     ∘ flip GraphBuilder.runT net
                                     ∘ registerSuccs   CONNECTION
{-# INLINE   buildNetworkM #-}
{-# INLINE rebuildNetworkM' #-}


class NetworkBuilderT net m n | m -> n, m -> net where runNetworkBuilderT :: net -> m a -> n (a, net)

instance {-# OVERLAPPABLE #-} NetworkBuilderT I IM IM where runNetworkBuilderT = impossible
instance {-# OVERLAPPABLE #-}
    ( m9 ~ Listener NODE_REMOVE       MemberRemove   m8
    , m8 ~ Listener CONNECTION_REMOVE SuccUnregister m7
    , m7 ~ Listener SUBGRAPH_INCLUDE  MemberRegister m6
    , m6 ~ Listener CONNECTION        SuccRegister   m5
    , m5 ~ GraphBuilder.BuilderT (Hetero (NEC.Graph n e c)) m4
    , m4 ~ Listener ELEMENT (TypeConstraint Equality_Full (Ref Node NetNode)) m3
    , m3 ~ Listener CONNECTION (TypeConstraint Equality_Full (Ref Edge (Link NetNode))) m2
    , m2 ~ Type.TypeBuilderT (Ref Node NetNode) m1
    , m1 ~ Self.SelfBuilderT (Ref Node NetNode) m
    , Monad m
    , net ~ Hetero (NEC.Graph n e c)
    ) => NetworkBuilderT net m9 m where
    runNetworkBuilderT net = flip Self.evalT (undefined ::        Ref Node NetNode)
                           ∘ flip Type.evalT (Nothing   :: Maybe (Ref Node NetNode))
                           ∘ constrainTypeEq CONNECTION (Proxy :: Proxy $ Ref Edge (Link NetNode))
                           ∘ constrainTypeEq ELEMENT    (Proxy :: Proxy $ Ref Node NetNode)
                           ∘ flip GraphBuilder.runT net
                           ∘ registerSuccs   CONNECTION
                           ∘ registerMembers SUBGRAPH_INCLUDE
                           ∘ unregisterSuccs CONNECTION_REMOVE
                           ∘ removeMembers   NODE_REMOVE

class NetworkBuilderT2 net m n | m -> n, m -> net where runNetworkBuilderT2 :: net -> m a -> n (a, net)

instance {-# OVERLAPPABLE #-} NetworkBuilderT2 I IM IM where runNetworkBuilderT2 = impossible
instance {-# OVERLAPPABLE #-}
    ( m9 ~ Listener NODE_REMOVE       MemberRemove   m8
    , m8 ~ Listener CONNECTION_REMOVE SuccUnregister m7
    , m7 ~ Listener SUBGRAPH_INCLUDE  MemberRegister m6
    , m6 ~ Listener CONNECTION        SuccRegister   m5
    , m5 ~ GraphBuilder.BuilderT (Hetero (NEC.MGraph (PrimState m) n e c)) m4
    , m4 ~ Listener ELEMENT (TypeConstraint Equality_Full (Ref Node NetNode)) m3
    , m3 ~ Listener CONNECTION (TypeConstraint Equality_Full (Ref Edge (Link NetNode))) m2
    , m2 ~ Type.TypeBuilderT (Ref Node NetNode) m1
    , m1 ~ Self.SelfBuilderT (Ref Node NetNode) m
    , PrimMonad m
    , net ~ Hetero (NEC.Graph n e c)
    ) => NetworkBuilderT2 net m9 m where
    runNetworkBuilderT2 net mf = do
        net' <- mapM NEC.unsafeThaw net
        (a, netout) <- flip Self.evalT (undefined ::        Ref Node NetNode)
                     ∘ flip Type.evalT (Nothing   :: Maybe (Ref Node NetNode))
                     ∘ constrainTypeEq CONNECTION (Proxy :: Proxy $ Ref Edge (Link NetNode))
                     ∘ constrainTypeEq ELEMENT    (Proxy :: Proxy $ Ref Node NetNode)
                     ∘ flip GraphBuilder.runT net'
                     ∘ registerSuccs   CONNECTION
                     ∘ registerMembers SUBGRAPH_INCLUDE
                     ∘ unregisterSuccs CONNECTION_REMOVE
                     ∘ removeMembers   NODE_REMOVE
                     $ mf
        netout' <- mapM NEC.unsafeFreeze netout
        return (a, netout')

runNetworkBuilderT' net = flip Self.evalT (undefined ::        Ref Node NetNode')
                        ∘ flip Type.evalT (Nothing   :: Maybe (Ref Node NetNode'))
                        ∘ constrainTypeM1 CONNECTION (Proxy :: Proxy $ Ref Edge c)
                        ∘ constrainTypeEq ELEMENT    (Proxy :: Proxy $ Ref Node NetNode')
                        ∘ flip GraphBuilder.runT net
                        ∘ registerSuccs   CONNECTION
                        ∘ registerMembers SUBGRAPH_INCLUDE
                        ∘ unregisterSuccs CONNECTION_REMOVE
                        ∘ removeMembers   NODE_REMOVE

runNetworkBuilderT_1 net = flip Self.evalT (undefined ::        Ref Node NetNode)
                         ∘ flip Type.evalT (Nothing   :: Maybe (Ref Node NetNode))
                         ∘ constrainTypeEq CONNECTION (Proxy :: Proxy $ Ref Edge (Link NetNode))
                         ∘ constrainTypeEq ELEMENT    (Proxy :: Proxy $ Ref Node NetNode)
                         ∘ flip GraphBuilder.runT net
                         ∘ registerSuccs   CONNECTION
                         ∘ registerMembers SUBGRAPH_INCLUDE
                         ∘ unregisterSuccs CONNECTION_REMOVE
                         ∘ removeMembers   NODE_REMOVE

-- FIXME[WD]: poprawic typ oraz `WithElement_` (!)
-- FIXME[WD]: inputs should be more general and should be refactored out
inputstmp :: forall layout term rt x.
      (MapTryingElemList_
                            (Elems term (ByDynamics rt Lit.String x) x)
                            (TFoldable x)
                            (Term layout term rt), x ~ Layout layout term rt) => Term layout term rt -> [x]
inputstmp a = withElement_ (p :: P (TFoldable x)) (foldrT (:) []) a



type instance Prop Inputs (Term layout term rt) = [Layout layout term rt]
instance (MapTryingElemList_
                           (Elems
                              term
                              (ByDynamics rt Lit.String (Layout layout term rt))
                              (Layout layout term rt))
                           (TFoldable (Layout layout term rt))
                           (Term layout term rt)) => Getter Inputs (Term layout term rt) where getter _ = inputstmp

type HasInputs n e = (OverElement (MonoTFunctor e) (RecordOf n), HasRecord n)

fmapInputs :: HasInputs n e => (e -> e) -> (n -> n)
fmapInputs (f :: e -> e) a = a & record %~ overElement (p :: P (MonoTFunctor e)) (monoTMap f)

