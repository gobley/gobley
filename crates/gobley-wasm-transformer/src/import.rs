/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

use std::error::Error;
use std::fmt;
use std::str::FromStr;

use walrus::ir::Value;
use walrus::{ConstExpr, ElementItems, ElementKind, FunctionId, Module, ValType};

use crate::Transformer;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum WasmType {
    I32,
    I64,
    F32,
    F64,
    /* Others are not supported yet */
}

impl From<WasmType> for ValType {
    fn from(value: WasmType) -> Self {
        match value {
            WasmType::I32 => ValType::I32,
            WasmType::I64 => ValType::I64,
            WasmType::F32 => ValType::F32,
            WasmType::F64 => ValType::F64,
        }
    }
}

impl FromStr for WasmType {
    type Err = UnknownWasmType;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(match s {
            "i32" => Self::I32,
            "i64" => Self::I64,
            "f32" => Self::F32,
            "f64" => Self::F64,
            _ => return Err(UnknownWasmType),
        })
    }
}

#[derive(Debug, Clone, Copy)]
pub struct UnknownWasmType;

impl fmt::Display for UnknownWasmType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "unknown or unsupported wasm type")
    }
}

impl Error for UnknownWasmType {}

#[derive(Debug, Clone)]
pub struct WasmFunctionImport {
    pub module: String,
    pub name: String,
    pub params: Vec<WasmType>,
    pub result: Option<WasmType>,
}

impl FromStr for WasmFunctionImport {
    type Err = InvalidWasmFunctionImport;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let mut split = s.split(':');

        let module = split
            .next()
            .ok_or(InvalidWasmFunctionImport::MissingModuleName)?;
        let name = split
            .next()
            .ok_or(InvalidWasmFunctionImport::MissingFunctionName)?;
        let params_str = split
            .next()
            .ok_or(InvalidWasmFunctionImport::MissingParams)?;

        let mut params = vec![];
        for (param_idx, param) in params_str
            .split_inclusive(&[/* 2 of 32 */ '2', /* 4 of 64 */ '4'])
            .map(WasmType::from_str)
            .enumerate()
        {
            let Ok(param) = param else {
                return Err(InvalidWasmFunctionImport::InvalidParam(param_idx));
            };
            params.push(param);
        }

        let result = split
            .next()
            .ok_or(InvalidWasmFunctionImport::MissingResultType)?;
        let result = if result.is_empty() {
            None
        } else {
            Some(WasmType::from_str(result).map_err(|_| InvalidWasmFunctionImport::InvalidResult)?)
        };

        if split.next().is_some() {
            return Err(InvalidWasmFunctionImport::TooManyFields);
        }

        Ok(WasmFunctionImport {
            module: module.to_string(),
            name: name.to_string(),
            params,
            result,
        })
    }
}

#[derive(Debug, Clone, Copy)]
pub enum InvalidWasmFunctionImport {
    MissingModuleName,
    MissingFunctionName,
    MissingParams,
    MissingResultType,
    TooManyFields,
    InvalidParam(usize),
    InvalidResult,
}

impl fmt::Display for InvalidWasmFunctionImport {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::MissingModuleName => write!(f, "missing module name"),
            Self::MissingFunctionName => write!(f, "missing function name"),
            Self::MissingParams => write!(f, "missing param list"),
            Self::MissingResultType => write!(f, "missing resul type"),
            Self::TooManyFields => write!(f, "too many fields"),
            Self::InvalidParam(idx) => write!(f, "invalid param type at index {idx}"),
            Self::InvalidResult => write!(f, "invalid result type"),
        }
    }
}

impl Error for InvalidWasmFunctionImport {}

impl Transformer {
    pub(crate) fn inject_function_imports(&mut self) {
        let mut function_ids = vec![];
        for function_import in std::mem::take(&mut self.function_imports) {
            let function_id = Self::inject_function_import(
                &mut self.module,
                &function_import.module,
                &function_import.name,
                function_import
                    .params
                    .into_iter()
                    .map(Into::into)
                    .collect::<Vec<_>>()
                    .as_slice(),
                function_import.result.map(Into::into).as_ref(),
            );
            function_ids.push(function_id);
        }

        if let Some(main_function_table_id) =
            self.module.tables.main_function_table().ok().flatten()
        {
            let main_function_table = self.module.tables.get_mut(main_function_table_id);
            if let Some(maximum) = &mut main_function_table.maximum {
                let num_functions_added = function_ids.len();
                self.module.elements.add(
                    ElementKind::Active {
                        table: main_function_table_id,
                        offset: ConstExpr::Value(Value::I32(*maximum as i32)),
                    },
                    ElementItems::Functions(function_ids),
                );
                *maximum += num_functions_added as u64;
                main_function_table.initial = *maximum;
            }
        }
    }

    fn inject_function_import(
        module: &mut Module,
        module_name: &str,
        name: &str,
        params: &[ValType],
        result: Option<&ValType>,
    ) -> FunctionId {
        let ty = module
            .types
            .add(params, result.map(std::slice::from_ref).unwrap_or(&[]));
        let (function_id, _) = module.add_import_func(module_name, name, ty);
        function_id
    }
}
