/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import SwiftUI
import TokioBoringAppKotlin

struct ContentView : UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        TokioBoringAppUIViewControllerKt.TokioBoringAppUIViewController()
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

#Preview {
    ContentView()
}
