module Luna.Syntax.AST.Function.Class where

import Prelude.Luna

import Luna.Syntax.AST.Function.Argument


data Method   a body = Method   { __self_ :: a
                                , __func_ :: Function a body
                                } deriving (Show, Functor, Foldable, Traversable)

data Function a body = Function { __args_ :: [ArgDef a]
                                , __out_  :: body
                                } deriving (Show, Functor, Foldable, Traversable)
