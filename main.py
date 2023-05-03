from m1.m1app import M1App

if __name__ == "__main__":
    app = M1App()
    app.eval('tk::PlaceWindow . center')
    app.mainloop()